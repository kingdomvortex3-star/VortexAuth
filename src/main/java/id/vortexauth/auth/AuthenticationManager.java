
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.session.SessionManager;

import java.sql.SQLException;
import java.util.UUID;

public final class AuthenticationManager {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final RegistrationManager registrationManager;
    private final LoginAttemptManager attemptManager;
    private final SessionManager sessionManager;

    public AuthenticationManager(
            VortexAuth plugin,
            AccountManager accountManager,
            RegistrationManager registrationManager,
            LoginAttemptManager attemptManager,
            SessionManager sessionManager
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.registrationManager = registrationManager;
        this.attemptManager = attemptManager;
        this.sessionManager = sessionManager;
    }

    public AuthResult register(
            UUID uuid,
            String username,
            String password,
            String confirmation,
            String platform,
            String xuid
    ) {
        return registrationManager.register(
                uuid,
                username,
                password,
                confirmation,
                platform,
                xuid
        );
    }

    public AuthResult login(
            UUID uuid,
            String password,
            String ipHash
    ) {
        if (uuid == null || password == null) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        ) || !plugin.getConfig().getBoolean(
                "authentication.login.enabled", true
        )) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (sessionManager.isAuthenticated(uuid)) {
            return AuthResult.ALREADY_LOGGED_IN;
        }

        if (attemptManager.isLocked(uuid)) {
            return AuthResult.ACCOUNT_LOCKED;
        }

        try {
            if (!accountManager.exists(uuid)) {
                return AuthResult.NOT_REGISTERED;
            }

            boolean successful = accountManager.login(
                    uuid,
                    password
            );

            if (!successful) {
                boolean locked =
                        attemptManager.recordFailedAttempt(uuid);

                return locked
                        ? AuthResult.TOO_MANY_ATTEMPTS
                        : AuthResult.INVALID_PASSWORD;
            }

            attemptManager.resetAttempts(uuid);

            sessionManager.createSession(uuid, ipHash);

            return AuthResult.SUCCESS;

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Database error during login: "
                            + exception.getMessage()
            );

            return AuthResult.DATABASE_ERROR;
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Authentication error: "
                            + exception.getMessage()
            );

            return AuthResult.INTERNAL_ERROR;
        }
    }

    public AuthResult login(UUID uuid, String password) {
        return login(uuid, password, null);
    }

    public boolean logout(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        boolean authenticated =
                sessionManager.isAuthenticated(uuid);

        sessionManager.invalidateSession(uuid);

        return authenticated;
    }

    public boolean isAuthenticated(UUID uuid) {
        return sessionManager.isAuthenticated(uuid);
    }

    public boolean isRegistered(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        try {
            return accountManager.exists(uuid);
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Unable to check player account: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    public boolean isLocked(UUID uuid) {
        return attemptManager.isLocked(uuid);
    }

    public int getRemainingAttempts(UUID uuid) {
        return attemptManager.getRemainingAttempts(uuid);
    }

    public long getRemainingLockTime(UUID uuid) {
        return attemptManager.getRemainingLockMillis(uuid);
    }

    public void invalidateSession(UUID uuid) {
        sessionManager.invalidateSession(uuid);
    }

    public void shutdown() {
        sessionManager.invalidateAll();
    }
}
