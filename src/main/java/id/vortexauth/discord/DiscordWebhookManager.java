
package id.vortexauth.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import id.vortexauth.VortexAuth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class DiscordWebhookManager {

    private final VortexAuth plugin;
    private final HttpClient httpClient;
    private final Gson gson;

    public DiscordWebhookManager(VortexAuth plugin) {
        this.plugin = plugin;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.gson = new Gson();
    }

    public void sendRegister(String username) {
        sendEvent(
                "register",
                "New Registration",
                "Player **" + safe(username) + "** registered an account.",
                5763719
        );
    }

    public void sendLogin(String username) {
        sendEvent(
                "login",
                "Successful Login",
                "Player **" + safe(username) + "** logged in.",
                5763719
        );
    }

    public void sendFailedLogin(String username) {
        sendEvent(
                "failed-login",
                "Failed Login",
                "Player **" + safe(username) + "** entered an incorrect password.",
                15548997
        );
    }

    public void sendPremiumEnabled(String username) {
        sendEvent(
                "premium-enabled",
                "Premium Authentication",
                "Premium verification completed for **"
                        + safe(username) + "**.",
                5793266
        );
    }

    public void sendVpnBlocked(String username) {
        sendEvent(
                "vpn-blocked",
                "VPN Connection Blocked",
                "Connection from player **"
                        + safe(username) + "** was blocked.",
                15548997
        );
    }

    public void sendBotDetected(String username) {
        sendEvent(
                "bot-detected",
                "Bot Detection",
                "Suspicious connection detected from **"
                        + safe(username) + "**.",
                15548997
        );
    }

    public void sendEvent(
            String configEvent,
            String title,
            String description,
            int color
    ) {
        if (!plugin.getConfig().getBoolean(
                "discord.enabled", false
        )) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "discord.events." + configEvent, true
        )) {
            return;
        }

        String webhookUrl = plugin.getConfig().getString(
                "discord.webhook-url", ""
        );

        if (!isValidWebhook(webhookUrl)) {
            plugin.getLogger().warning(
                    "Discord webhook URL is missing or invalid."
            );
            return;
        }

        JsonObject payload = new JsonObject();

        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();

        embed.addProperty("title", safe(title));
        embed.addProperty("description", safe(description));
        embed.addProperty("color", color);
        embed.addProperty("timestamp",
                java.time.Instant.now().toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "VortexAuth Security");
        embed.add("footer", footer);

        embeds.add(embed);
        payload.add("embeds", embeds);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        gson.toJson(payload)
                ))
                .build();

        httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.discarding()
        ).thenAccept(response -> {
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                plugin.getLogger().warning(
                        "Discord webhook returned HTTP "
                                + response.statusCode()
                );
            }
        }).exceptionally(error -> {
            plugin.getLogger().warning(
                    "Failed to send Discord webhook: "
                            + error.getClass().getSimpleName()
            );
            return null;
        });
    }

    private boolean isValidWebhook(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);

            String host = uri.getHost();

            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && (
                        host.equalsIgnoreCase("discord.com")
                        || host.equalsIgnoreCase("discordapp.com")
                    )
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/api/webhooks/");

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String safe(String text) {
        if (text == null) {
            return "Unknown";
        }

        String sanitized = text
                .replace("@", "@\u200B")
                .replace("`", "'");

        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500);
        }

        return sanitized;
    }
}
