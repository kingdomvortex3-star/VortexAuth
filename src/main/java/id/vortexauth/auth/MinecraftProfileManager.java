
package id.vortexauth.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MinecraftProfileManager {

    private static final String PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private final HttpClient httpClient;

    public MinecraftProfileManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<MinecraftProfile> getProfile(
            XstsManager.XstsToken xstsToken
    ) {
        if (xstsToken == null
                || xstsToken.token() == null
                || xstsToken.token().isBlank()
                || xstsToken.userHash() == null
                || xstsToken.userHash().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Invalid XSTS credentials."
                    )
            );
        }

        String authorization = "XBL3.0 x="
                + xstsToken.userHash()
                + ";"
                + xstsToken.token();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() == 404) {
                throw new IllegalStateException(
                        "This Microsoft account does not own a Minecraft Java profile."
                );
            }

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Minecraft profile verification failed. HTTP "
                                + response.statusCode()
                );
            }

            JsonObject json = JsonParser.parseString(
                    response.body()
            ).getAsJsonObject();

            String id = requiredString(json, "id");
            String name = requiredString(json, "name");

            UUID minecraftUuid;

            try {
                if (!id.matches("[0-9a-fA-F]{32}")) {
                    throw new IllegalArgumentException(
                            "Invalid Minecraft profile UUID."
                    );
                }

                minecraftUuid = UUID.fromString(
                        id.replaceFirst(
                                "(........)(....)(....)(....)(............)",
                                "$1-$2-$3-$4-$5"
                        )
                );

            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Minecraft returned an invalid profile UUID.",
                        exception
                );
            }

            return new MinecraftProfile(
                    minecraftUuid,
                    name
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
                    "Missing Minecraft profile field: " + key
            );
        }

        String value = object.get(key).getAsString();

        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Empty Minecraft profile field: " + key
            );
        }

        return value;
    }

    public record MinecraftProfile(
            UUID uuid,
            String username
    ) {
    }
}
