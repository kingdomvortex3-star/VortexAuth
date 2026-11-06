
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

public final class XboxLiveManager {

    private static final String AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate";

    private final HttpClient httpClient;

    public XboxLiveManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<XboxToken> authenticate(
            MicrosoftOAuthManager.MicrosoftToken microsoftToken
    ) {
        if (microsoftToken == null
                || microsoftToken.accessToken() == null
                || microsoftToken.accessToken().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Microsoft access token is missing."
                    )
            );
        }

        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty(
                "SiteName",
                "user.auth.xboxlive.com"
        );
        properties.addProperty(
                "RpsTicket",
                "d=" + microsoftToken.accessToken()
        );

        JsonObject payload = new JsonObject();
        payload.add("Properties", properties);
        payload.addProperty(
                "RelyingParty",
                "http://auth.xboxlive.com"
        );
        payload.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
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
                        "Xbox Live authentication failed. HTTP "
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
                        "Xbox Live user claims are missing."
                );
            }

            JsonArray xui = displayClaims.getAsJsonArray("xui");

            if (xui.isEmpty()) {
                throw new IllegalStateException(
                        "Xbox Live user information is empty."
                );
            }

            JsonObject user = xui.get(0).getAsJsonObject();

            String userHash = requiredString(user, "uhs");

            return new XboxToken(
                    token,
                    userHash
            );
        });
    }

    private String requiredString(
            JsonObject object,
            String key
    ) {
        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            throw new IllegalStateException(
                    "Missing Xbox response field: " + key
            );
        }

        String value = object.get(key).getAsString();

        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Empty Xbox response field: " + key
            );
        }

        return value;
    }

    public record XboxToken(
            String token,
            String userHash
    ) {
    }
}
