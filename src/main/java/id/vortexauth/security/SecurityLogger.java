
package id.vortexauth.security;

import id.vortexauth.VortexAuth;
import id.vortexauth.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public final class SecurityLogger {

    private final VortexAuth plugin;
    private final DatabaseManager databaseManager;

    public SecurityLogger(
            VortexAuth plugin,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void log(
            UUID playerUuid,
            String eventType,
            String details
    ) {
        if (eventType == null || eventType.isBlank()) {
            return;
        }

        String safeDetails = details == null ? "" : details;

        if (safeDetails.length() > 1000) {
            safeDetails = safeDetails.substring(0, 1000);
        }

        final String finalDetails = safeDetails;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO vortexauth_security "
                             + "(player_uuid, event_type, details, created_at) "
                             + "VALUES (?, ?, ?, ?)"
             )) {

            statement.setString(
                    1,
                    playerUuid == null ? null : playerUuid.toString()
            );
            statement.setString(2, eventType);
            statement.setString(3, finalDetails);
            statement.setLong(4, System.currentTimeMillis());

            statement.executeUpdate();

        } catch (SQLException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Failed to save security event: " + eventType,
                    exception
            );
        }

        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info(
                    "[Security] " + eventType + " | " + finalDetails
            );
        }
    }

    public void logLoginSuccess(UUID uuid, String username) {
        log(uuid, "LOGIN_SUCCESS", "Username: " + username);
    }

    public void logLoginFailed(UUID uuid, String username) {
        log(uuid, "LOGIN_FAILED", "Username: " + username);
    }

    public void logRegistration(UUID uuid, String username) {
        log(uuid, "REGISTER", "Username: " + username);
    }

    public void logBruteForce(UUID uuid, int attempts) {
        log(
                uuid,
                "BRUTE_FORCE",
                "Failed attempts: " + attempts
        );
    }

    public void logSuspiciousActivity(UUID uuid, String details) {
        log(uuid, "SUSPICIOUS_ACTIVITY", details);
    }

    public void logPremiumVerification(UUID uuid, boolean success) {
        log(
                uuid,
                "PREMIUM_VERIFICATION",
                "Success: " + success
        );
    }

    public void logVpnBlocked(UUID uuid, String provider) {
        log(
                uuid,
                "VPN_BLOCKED",
                "Provider: " + provider
        );
    }
}
