
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.utils.SecurityUtils;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public final class RegistrationManager {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final PasswordManager passwordManager;

    public RegistrationManager(
            VortexAuth plugin,
            AccountManager accountManager,
            PasswordManager passwordManager
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.passwordManager = passwordManager;
    }

    public AuthResult register(
            UUID uuid,
            String username,
            String password,
            String confirmation,
            String platform,
            String xuid
    ) {
        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        ) || !plugin.getConfig().getBoolean(
                "authentication.register.enabled", true
        )) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (uuid == null
                || username == null
                || password == null
                || platform == null) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (!SecurityUtils.isValidUsername(username)) {
            return AuthResult.INVALID_USERNAME;
        }

        String normalizedPlatform =
                platform.toUpperCase(Locale.ROOT);

        if (!normalizedPlatform.equals("JAVA")
                && !normalizedPlatform.equals("BEDROCK")) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (normalizedPlatform.equals("JAVA")
                && !plugin.getConfig().getBoolean(
                "java.enabled", true
        )) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (normalizedPlatform.equals("BEDROCK")) {
            return AuthResult.BEDROCK_NOT_SUPPORTED;
        }

        if (!passwordManager.isValidPassword(password)) {
            int minimum = plugin.getConfig().getInt(
                    "authentication.register.min-password-length",
                    6
            );

            int maximum = plugin.getConfig().getInt(
                    "authentication.register.max-password-length",
                    32
            );

            if (password.length() < minimum) {
                return AuthResult.PASSWORD_TOO_SHORT;
            }

            if (password.length() > maximum
                    || password.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
            ).length > 72) {
                return AuthResult.PASSWORD_TOO_LONG;
            }

            return AuthResult.INVALID_PASSWORD;
        }

        if (plugin.getConfig().getBoolean(
                "authentication.register.require-confirmation",
                true
        )) {
            if (!passwordManager.passwordsMatch(
                    password,
                    confirmation
            )) {
                return AuthResult.PASSWORD_MISMATCH;
            }
        }

        try {
            if (accountManager.exists(uuid)) {
                return AuthResult.ALREADY_REGISTERED;
            }

            if (accountManager.getAccountByUsername(
                    username
            ).isPresent()) {
                return AuthResult.ALREADY_REGISTERED;
            }

            boolean registered = accountManager.register(
                    uuid,
                    username,
                    password,
                    "JAVA",
                    null
            );

            return registered
                    ? AuthResult.SUCCESS
                    : AuthResult.INTERNAL_ERROR;

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Database error during registration: "
                            + exception.getMessage()
            );

            return AuthResult.DATABASE_ERROR;

        } catch (IllegalArgumentException exception) {
            return AuthResult.INVALID_PASSWORD;
        }
    }
}
