
package id.vortexauth.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import id.vortexauth.VortexAuth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MicrosoftOAuthManager {

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";

    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private static final String SCOPE =
            "XboxLive.signin offline_access";

    private final VortexAuth plugin;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public MicrosoftOAuthManager(VortexAuth plugin) {
        this.plugin = plugin;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "VortexAuth-Microsoft-OAuth");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<DeviceCode> requestDeviceCode() {
        String clientId = plugin.getConfig().getString(
                "premium.microsoft.client-id", ""
        );

        if (clientId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Microsoft OAuth Client ID is not configured."
                    )
            );
        }

        String body = form(
                "client_id", clientId,
                "scope", SCOPE
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEVICE_CODE_URL))
                .timeout(Duration.ofSeconds(15))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            JsonObject json = parseResponse(response);

            return new DeviceCode(
                    json.get("device_code").getAsString(),
                    json.get("user_code").getAsString(),
                    json.get("verification_uri").getAsString(),
                    json.get("expires_in").getAsInt(),
                    json.has("interval")
                            ? json.get("interval").getAsInt()
                            : 5
            );
        });
    }

    public CompletableFuture<MicrosoftToken> pollForToken(
            DeviceCode deviceCode
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String clientId = plugin.getConfig().getString(
                    "premium.microsoft.client-id", ""
            );

            if (clientId.isBlank()) {
                throw new IllegalStateException(
                        "Microsoft OAuth Client ID is not configured."
                );
            }

            long expiry = System.currentTimeMillis()
                    + TimeUnit.SECONDS.toMillis(
                            deviceCode.expiresIn()
                    );

            int interval = Math.max(1, deviceCode.interval());

            while (System.currentTimeMillis() < expiry
                    && !Thread.currentThread().isInterrupted()) {

                try {
                    Thread.sleep(interval * 1000L);

                    String body = form(
                            "grant_type",
                            "urn:ietf:params:oauth:grant-type:device_code",
                            "client_id",
                            clientId,
                            "device_code",
                            deviceCode.deviceCode()
                    );

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(TOKEN_URL))
                            .timeout(Duration.ofSeconds(15))
                            .header(
                                    "Content-Type",
                                    "application/x-www-form-urlencoded"
                            )
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    JsonObject json = JsonParser.parseString(
                            response.body()
                    ).getAsJsonObject();

                    if (response.statusCode() == 200
                            && json.has("access_token")) {

                        return new MicrosoftToken(
                                json.get("access_token").getAsString(),
                                json.has("refresh_token")
                                        ? json.get("refresh_token").getAsString()
                                        : null,
                                json.get("expires_in").getAsInt()
                        );
                    }

                    String error = json.has("error")
                            ? json.get("error").getAsString()
                            : "unknown_error";

                    switch (error) {
                        case "authorization_pending":
                            continue;

                        case "slow_down":
                            interval += 5;
                            continue;

                        case "authorization_declined":
                            throw new IllegalStateException(
                                    "Microsoft authorization was declined."
                            );

                        case "expired_token":
                            throw new IllegalStateException(
                                    "Microsoft device code expired."
                            );

                        case "bad_verification_code":
                            throw new IllegalStateException(
                                    "Invalid Microsoft device code."
                            );

                        default:
                            throw new IllegalStateException(
                                    "Microsoft OAuth error: " + error
                            );
                    }

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "Microsoft OAuth polling interrupted."
                    );

                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(
                            "Microsoft OAuth network error.",
                            exception
                    );
                }
            }

            throw new IllegalStateException(
                    "Microsoft device authorization timed out."
            );

        }, executor);
    }

    private JsonObject parseResponse(
            HttpResponse<String> response
    ) {
        JsonObject json = JsonParser.parseString(
                response.body()
        ).getAsJsonObject();

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            String error = json.has("error")
                    ? json.get("error").getAsString()
                    : "Unknown OAuth error";

            throw new IllegalStateException(
                    "Microsoft OAuth failed: " + error
            );
        }

        return json;
    }

    private String form(String... values) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < values.length; i += 2) {
            if (result.length() > 0) {
                result.append("&");
            }

            result.append(URLEncoder.encode(
                    values[i],
                    StandardCharsets.UTF_8
            ));

            result.append("=");

            result.append(URLEncoder.encode(
                    values[i + 1],
                    StandardCharsets.UTF_8
            ));
        }

        return result.toString();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public record DeviceCode(
            String deviceCode,
            String userCode,
            String verificationUri,
            int expiresIn,
            int interval
    ) {
    }

    public record MicrosoftToken(
            String accessToken,
            String refreshToken,
            int expiresIn
    ) {
    }
}
