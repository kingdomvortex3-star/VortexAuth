
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.session.SessionManager;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class AuthenticationManager {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final RegistrationManager registrationManager;
    private final LoginAttemptManager loginAttemptManager;
    private final SessionManager sessionManager;

    public AuthenticationManager(
            VortexAuth plugin,
            AccountManager accountManager,
            RegistrationManager registrationManager,
            LoginAttemptManager loginAttemptManager,
            SessionManager sessionManager
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.registrationManager = registrationManager;
        this.loginAttemptManager = loginAttemptManager;
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
        if (uuid == null) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (isAuthenticated(uuid)) {
            return AuthResult.ALREADY_LOGGED_IN;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        )) {
            return AuthResult.INTERNAL_ERROR;
        }

        return registrationManager.register(
                uuid,
                username,
                password,
                confirmation,
                platform,
                xuid
        );
    }

    public AuthResult login(UUID uuid, String password) {
        return login(uuid, password, null);
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
        )) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (sessionManager.isAuthenticated(uuid)) {
            return AuthResult.ALREADY_LOGGED_IN;
        }

        if (loginAttemptManager.isLocked(uuid)) {
            return AuthResult.ACCOUNT_LOCKED;
        }

        try {
            Optional<AccountManager.Account> optionalAccount =
                    accountManager.getAccount(uuid);

            if (optionalAccount.isEmpty()) {
                return AuthResult.NOT_REGISTERED;
            }

            boolean valid = accountManager.login(uuid, password);

            if (!valid) {
                boolean locked =
                        loginAttemptManager.recordFailedAttempt(uuid);

                return locked
                        ? AuthResult.TOO_MANY_ATTEMPTS
                        : AuthResult.INVALID_PASSWORD;
            }

            loginAttemptManager.resetAttempts(uuid);
            sessionManager.createSession(uuid, ipHash);

            return AuthResult.SUCCESS;

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Database authentication error: "
                            + exception.getMessage()
            );

            return AuthResult.DATABASE_ERROR;

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Unexpected authentication error: "
                            + exception.getMessage()
            );

            return AuthResult.INTERNAL_ERROR;
        }
    }

    public boolean logout(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        sessionManager.invalidateSession(uuid);
        return true;
    }

    public boolean isAuthenticated(UUID uuid) {
        return uuid != null
                && sessionManager.isAuthenticated(uuid);
    }

    public boolean isAuthenticated(
            UUID uuid,
            String ipHash
    ) {
        return uuid != null
                && sessionManager.isAuthenticated(uuid, ipHash);
    }

    public boolean isRegistered(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        try {
            return accountManager.exists(uuid);
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to check account registration: "
                            + exception.getMessage()
            );
            return false;
        }
    }

    public boolean isLocked(UUID uuid) {
        return uuid != null
                && loginAttemptManager.isLocked(uuid);
    }

    public int getRemainingAttempts(UUID uuid) {
        return uuid == null
                ? 0
                : loginAttemptManager.getRemainingAttempts(uuid);
    }

    public long getRemainingLockTime(UUID uuid) {
        return uuid == null
                ? 0L
                : loginAttemptManager.getRemainingLockTime(uuid);
    }

    public void createBedrockSession(UUID uuid, String ipHash) {
        if (uuid == null || ipHash == null) {
            return;
        }

        try {
            if (!accountManager.exists(uuid)) {
                return;
            }

            sessionManager.createSession(uuid, ipHash);
            accountManager.updateLastLogin(uuid);
            loginAttemptManager.resetAttempts(uuid);

        } catch (SQLException exception) {
            sessionManager.invalidateSession(uuid);

            plugin.getLogger().severe(
                    "Failed to create Bedrock session: "
                            + exception.getMessage()
            );
        }
    }

    public void invalidateSession(UUID uuid) {
        if (uuid != null) {
            sessionManager.invalidateSession(uuid);
        }
    }

    public void invalidateAllSessions() {
        sessionManager.invalidateAll();
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public LoginAttemptManager getLoginAttemptManager() {
        return loginAttemptManager;
    }

    public void shutdown() {
        sessionManager.invalidateAll();
    }
}
