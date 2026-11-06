
package id.vortexauth.security;

import id.vortexauth.VortexAuth;
import id.vortexauth.utils.SecurityUtils;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class IpAddressManager {

    private static final int SECRET_LENGTH = 32;

    private final VortexAuth plugin;
    private final byte[] secret;

    public IpAddressManager(VortexAuth plugin) throws IOException {
        this.plugin = plugin;

        Files.createDirectories(plugin.getDataFolder().toPath());

        Path secretFile = plugin.getDataFolder()
                .toPath()
                .resolve("ip-secret.key");

        if (Files.exists(secretFile)) {
            byte[] stored = Files.readAllBytes(secretFile);

            if (stored.length == SECRET_LENGTH) {
                this.secret = stored;
            } else {
                try {
                    byte[] decoded = Base64.getDecoder().decode(stored);

                    if (decoded.length != SECRET_LENGTH) {
                        throw new IOException("Invalid IP secret length.");
                    }

                    this.secret = decoded;
                } catch (IllegalArgumentException exception) {
                    throw new IOException(
                            "Invalid IP secret file.",
                            exception
                    );
                }
            }
        } else {
            this.secret = new byte[SECRET_LENGTH];

            new SecureRandom().nextBytes(this.secret);

            Files.write(secretFile, this.secret);
        }

        plugin.getLogger().info("IP address hashing initialized.");
    }

    public String getPlayerIpHash(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }

        InetSocketAddress address = player.getAddress();

        if (address == null || address.getAddress() == null) {
            return null;
        }

        return hashAddress(
                address.getAddress().getHostAddress()
        );
    }

    public String hashAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        try {
            return SecurityUtils.hashIp(
                    address.trim(),
                    secret
            );
        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Failed to hash IP address."
            );
            return null;
        }
    }

    public boolean matches(String address, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }

        String actualHash = hashAddress(address);

        if (actualHash == null) {
            return false;
        }

        return MessageDigest.isEqual(
                actualHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    public String getServerSecretIdentifier() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret);

            return HexFormat.of().formatHex(digest, 0, 8);

        } catch (Exception exception) {
            return "unavailable";
        }
    }
}
