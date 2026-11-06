
package id.vortexauth.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class XstsManager {

    private static final String XSTS_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";

    private final HttpClient httpClient;

    public XstsManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<XstsToken> authorize(
            XboxLiveManager.XboxToken xboxToken
    ) {
        if (xboxToken == null
                || xboxToken.token() == null
                || xboxToken.token().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Xbox Live token is missing."
                    )
            );
        }

        JsonObject properties = new JsonObject();

        properties.addProperty("SandboxId", "RETAIL");

        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken.token());

        properties.add("UserTokens", userTokens);

        JsonObject payload = new JsonObject();
        payload.add("Properties", properties);

        payload.addProperty(
                "RelyingParty",
                "rp://api.minecraftservices.com/"
        );

        payload.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(XSTS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        payload.toString()
                ))
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Xbox XSTS authorization failed. HTTP "
                                + response.statusCode()
                );
            }

            JsonObject json = JsonParser.parseString(
                    response.body()
            ).getAsJsonObject();

            String token = requiredString(json, "Token");

            JsonObject displayClaims = json
                    .getAsJsonObject("DisplayClaims");

            if (displayClaims == null
                    || !displayClaims.has("xui")) {
                throw new IllegalStateException(
                        "XSTS claims are missing."
                );
            }

            JsonArray xui = displayClaims.getAsJsonArray("xui");

            if (xui.isEmpty()) {
                throw new IllegalStateException(
                        "XSTS user claims are empty."
                );
            }

            JsonObject user = xui.get(0).getAsJsonObject();

            String userHash = requiredString(user, "uhs");

            return new XstsToken(token, userHash);
        });
    }

    private String requiredString(
            JsonObject object,
            String key
    ) {
        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            throw new IllegalStateException(
                    "Missing XSTS response field: " + key
            );
        }

        String value = object.get(key).getAsString();

        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Empty XSTS response field: " + key
            );
        }

        return value;
    }

    public record XstsToken(
            String token,
            String userHash
    ) {
    }
}
