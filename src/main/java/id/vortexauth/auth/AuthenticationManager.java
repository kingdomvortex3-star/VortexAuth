
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;

import java.sql.SQLException;
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

        if (!plugin.getConfig().getBoolean("authentication.enabled", true)) {
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

    public AuthResult login(UUID uuid, String password, String ipHash) {
        if (uuid == null || password == null) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (!plugin.getConfig().getBoolean("authentication.enabled", true)) {
            return AuthResult.INTERNAL_ERROR;
        }

        if (sessionManager.isAuthenticated(uuid)) {
            return AuthResult.ALREADY_LOGGED_IN;
        }

        if (loginAttemptManager.isLocked(uuid)) {
            return AuthResult.ACCOUNT_LOCKED;
        }

        try {
            AccountManager.Account account = accountManager.getAccount(uuid);

            if (account == null) {
                return AuthResult.NOT_REGISTERED;
            }

            boolean valid = accountManager.login(uuid, password);

            if (!valid) {
                loginAttemptManager.recordFailedAttempt(uuid);

                if (loginAttemptManager.isLocked(uuid)) {
                    return AuthResult.TOO_MANY_ATTEMPTS;
                }

                return AuthResult.INVALID_PASSWORD;
            }

            loginAttemptManager.resetAttempts(uuid);
            sessionManager.createSession(uuid, ipHash);

            return AuthResult.SUCCESS;

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to authenticate account " + uuid + ": "
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
        return uuid != null && sessionManager.isAuthenticated(uuid);
    }

    public boolean isAuthenticated(UUID uuid, String ipHash) {
        return uuid != null
                && sessionManager.isAuthenticated(uuid, ipHash);
    }

    public boolean isRegistered(UUID uuid) {
        return uuid != null && accountManager.exists(uuid);
    }

    public boolean isLocked(UUID uuid) {
        return uuid != null && loginAttemptManager.isLocked(uuid);
    }

    public void createBedrockSession(UUID uuid, String ipHash) {
        if (uuid == null) {
            return;
        }

        try {
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
