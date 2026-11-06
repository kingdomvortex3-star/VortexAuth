
package id.vortexauth.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import id.vortexauth.VortexAuth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AntiVpnManager {

    private final VortexAuth plugin;
    private final HttpClient httpClient;

    private final Map<String, CacheEntry> cache =
            new ConcurrentHashMap<>();

    private final Set<String> whitelist =
            ConcurrentHashMap.newKeySet();

    public AntiVpnManager(VortexAuth plugin) {
        this.plugin = plugin;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

        reloadWhitelist();
    }

    public CompletableFuture<VpnResult> check(String ipAddress) {
        if (!plugin.getConfig().getBoolean("anti-vpn.enabled", true)) {
            return CompletableFuture.completedFuture(
                    VpnResult.allowed("Anti-VPN disabled")
            );
        }

        if (ipAddress == null || ipAddress.isBlank()) {
            return CompletableFuture.completedFuture(
                    VpnResult.blocked("Invalid IP address")
            );
        }

        if (whitelist.contains(ipAddress)) {
            return CompletableFuture.completedFuture(
                    VpnResult.allowed("Whitelisted")
            );
        }

        CacheEntry cached = cache.get(ipAddress);

        if (cached != null
                && cached.expiresAt() > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached.result());
        }

        String apiKey = plugin.getConfig().getString(
                "anti-vpn.ipqualityscore.api-key", ""
        );

        if (apiKey.isBlank()) {
            return CompletableFuture.completedFuture(
                    VpnResult.allowed("API key not configured")
            );
        }

        String encodedIp = URLEncoder.encode(
                ipAddress,
                StandardCharsets.UTF_8
        );

        String url = "https://ipqualityscore.com/api/json/ip/"
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "/" + encodedIp;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(8))
                .GET()
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() != 200) {
                return VpnResult.unknown("API HTTP " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(
                    response.body()
            ).getAsJsonObject();

            if (json.has("success")
                    && !json.get("success").getAsBoolean()) {
                return VpnResult.unknown("API request unsuccessful");
            }

            boolean vpn = readBoolean(json, "vpn");
            boolean proxy = readBoolean(json, "proxy");
            boolean hosting = readBoolean(json, "is_crawler")
                    || readBoolean(json, "hosting_provider");
            boolean tor = readBoolean(json, "tor");

            boolean detected =
                    (vpn && plugin.getConfig().getBoolean(
                            "anti-vpn.check.vpn", true))
                    || (proxy && plugin.getConfig().getBoolean(
                            "anti-vpn.check.proxy", true))
                    || (hosting && plugin.getConfig().getBoolean(
                            "anti-vpn.check.hosting", true))
                    || (tor && plugin.getConfig().getBoolean(
                            "anti-vpn.check.tor", true));

            VpnResult result = detected
                    ? VpnResult.blocked("VPN/Proxy/Hosting/Tor detected")
                    : VpnResult.allowed("Connection accepted");

            long cacheMinutes = plugin.getConfig().getLong(
                    "anti-vpn.cache.duration-minutes", 60
            );

            if (plugin.getConfig().getBoolean(
                    "anti-vpn.cache.enabled", true
            )) {
                long expiry = System.currentTimeMillis()
                        + TimeUnit.MINUTES.toMillis(
                                Math.max(1, cacheMinutes)
                        );

                cache.put(
                        ipAddress,
                        new CacheEntry(result, expiry)
                );
            }

            return result;

        }).exceptionally(error ->
                VpnResult.unknown("VPN provider unavailable")
        );
    }

    private boolean readBoolean(JsonObject json, String key) {
        return json.has(key)
                && !json.get(key).isJsonNull()
                && json.get(key).getAsBoolean();
    }

    public void reloadWhitelist() {
        whitelist.clear();

        whitelist.addAll(
                plugin.getConfig().getStringList(
                        "anti-vpn.whitelist.ips"
                )
        );
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCachedAddressCount() {
        return cache.size();
    }

    public record VpnResult(
            boolean blocked,
            boolean known,
            String reason
    ) {
        public static VpnResult allowed(String reason) {
            return new VpnResult(false, true, reason);
        }

        public static VpnResult blocked(String reason) {
            return new VpnResult(true, true, reason);
        }

        public static VpnResult unknown(String reason) {
            return new VpnResult(false, false, reason);
        }
    }

    private record CacheEntry(
            VpnResult result,
            long expiresAt
    ) {}
}
