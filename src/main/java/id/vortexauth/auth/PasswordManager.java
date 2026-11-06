
package id.vortexauth.auth;

import org.mindrot.jbcrypt.BCrypt;

import id.vortexauth.VortexAuth;

import java.nio.charset.StandardCharsets;

public final class PasswordManager {

    private final VortexAuth plugin;

    private static final int DEFAULT_STRENGTH = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    public PasswordManager(VortexAuth plugin) {
        this.plugin = plugin;
    }

    public String hashPassword(String password) {
        validatePassword(password);

        int strength = plugin.getConfig().getInt(
                "authentication.password.bcrypt-strength",
                DEFAULT_STRENGTH
        );

        strength = Math.max(10, Math.min(strength, 14));

        return BCrypt.hashpw(
                password,
                BCrypt.gensalt(strength)
        );
    }

    public boolean verifyPassword(
            String password,
            String passwordHash
    ) {
        if (password == null || passwordHash == null) {
            return false;
        }

        if (password.getBytes(StandardCharsets.UTF_8).length
                > MAX_BCRYPT_BYTES) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, passwordHash);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Invalid password hash detected."
            );

            return false;
        }
    }

    public boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }

        int minimum = plugin.getConfig().getInt(
                "authentication.register.min-password-length",
                6
        );

        int maximum = plugin.getConfig().getInt(
                "authentication.register.max-password-length",
                32
        );

        if (password.length() < minimum
                || password.length() > maximum) {
            return false;
        }

        return password.getBytes(StandardCharsets.UTF_8).length
                <= MAX_BCRYPT_BYTES;
    }

    public void validatePassword(String password) {
        if (!isValidPassword(password)) {
            throw new IllegalArgumentException(
                    "Password does not meet VortexAuth requirements."
            );
        }
    }

    public boolean passwordsMatch(
            String password,
            String confirmation
    ) {
        if (password == null || confirmation == null) {
            return false;
        }

        return password.equals(confirmation);
    }
}
