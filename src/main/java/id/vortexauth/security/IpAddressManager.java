
package id.vortexauth.security;

import id.vortexauth.VortexAuth;
import id.vortexauth.utils.SecurityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.bukkit.entity.Player;

public final class IpAddressManager {

    private final VortexAuth plugin;
    private final byte[] secret;

    public IpAddressManager(VortexAuth plugin) throws IOException {
        this.plugin = plugin;
        this.secret = loadOrCreateSecret();
    }

    private byte[] loadOrCreateSecret() throws IOException {
        Path directory = plugin.getDataFolder().toPath();

        Files.createDirectories(directory);

        Path secretFile = directory.resolve("ip-secret.key");

        if (Files.exists(secretFile)) {
            String encoded = Files.readString(secretFile).trim();

            try {
                byte[] decoded = Base64.getDecoder().decode(encoded);

                if (decoded.length < 32) {
                    throw new IOException("IP secret key is invalid.");
                }

                return decoded;

            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Unable to read IP secret key.",
                        exception
                );
            }
        }

        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);

        String encoded = Base64.getEncoder().encodeToString(generated);

        Files.writeString(
                secretFile,
                encoded,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        return generated;
    }

    public String getPlayerIpHash(Player player) {
        if (player == null || player.getAddress() == null
                || player.getAddress().getAddress() == null) {
            return null;
        }

        String address = player.getAddress()
                .getAddress()
                .getHostAddress();

        return SecurityUtils.hashIp(address, secret);
    }

    public String hashAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        return SecurityUtils.hashIp(address.trim(), secret);
    }

    public boolean matches(String address, String storedHash) {
        if (address == null || storedHash == null) {
            return false;
        }

        String currentHash = hashAddress(address);

        return currentHash != null
                && SecurityUtils.constantTimeEquals(
                        currentHash,
                        storedHash
                );
    }

    public UUID getServerSecretIdentifier() {
        return UUID.nameUUIDFromBytes(secret);
    }
}
