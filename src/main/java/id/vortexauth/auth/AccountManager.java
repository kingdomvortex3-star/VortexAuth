
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Optional;
import java.util.UUID;

public final class AccountManager {

    private final VortexAuth plugin;
    private final DatabaseManager database;
    private final PasswordManager passwordManager;

    public AccountManager(
            VortexAuth plugin,
            DatabaseManager database,
            PasswordManager passwordManager
    ) {
        this.plugin = plugin;
        this.database = database;
        this.passwordManager = passwordManager;
    }

    public boolean register(
            UUID uuid,
            String username,
            String password,
            String platform,
            String xuid
    ) throws SQLException {

        if (uuid == null || username == null || platform == null) {
            return false;
        }

        if (!passwordManager.isValidPassword(password)) {
            return false;
        }

        if (getAccount(uuid).isPresent()) {
            return false;
        }

        if (getAccountByUsername(username).isPresent()) {
            return false;
        }

        if (xuid != null && getAccountByXuid(xuid).isPresent()) {
            return false;
        }

        String passwordHash =
                passwordManager.hashPassword(password);

        String sql = """
                INSERT INTO vortexauth_accounts
                (
                    uuid,
                    username,
                    password_hash,
                    platform,
                    xuid,
                    premium_enabled,
                    premium_verified,
                    created_at,
                    last_login
                )
                VALUES (?, ?, ?, ?, ?, FALSE, FALSE, ?, 0)
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, passwordHash);
            statement.setString(4, platform.toUpperCase());
            statement.setString(5, xuid);
            statement.setLong(6, System.currentTimeMillis());

            return statement.executeUpdate() == 1;
        }
    }

    public boolean login(
            UUID uuid,
            String password
    ) throws SQLException {

        Optional<Account> optionalAccount = getAccount(uuid);

        if (optionalAccount.isEmpty()) {
            return false;
        }

        Account account = optionalAccount.get();

        if (account.passwordHash() == null) {
            return false;
        }

        if (!passwordManager.verifyPassword(
                password,
                account.passwordHash()
        )) {
            return false;
        }

        updateLastLogin(uuid);

        return true;
    }

    public Optional<Account> getAccount(UUID uuid)
            throws SQLException {

        if (uuid == null) {
            return Optional.empty();
        }

        String sql = """
                SELECT *
                FROM vortexauth_accounts
                WHERE uuid = ?
                LIMIT 1
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapAccount(result));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Account> getAccountByUsername(
            String username
    ) throws SQLException {

        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String sql = """
                SELECT *
                FROM vortexauth_accounts
                WHERE username = ?
                LIMIT 1
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapAccount(result));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Account> getAccountByXuid(
            String xuid
    ) throws SQLException {

        if (xuid == null || xuid.isBlank()) {
            return Optional.empty();
        }

        String sql = """
                SELECT *
                FROM vortexauth_accounts
                WHERE xuid = ?
                LIMIT 1
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, xuid);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapAccount(result));
                }
            }
        }

        return Optional.empty();
    }

    public boolean exists(UUID uuid) throws SQLException {
        return getAccount(uuid).isPresent();
    }

    public boolean updateLastLogin(UUID uuid)
            throws SQLException {

        if (uuid == null) {
            return false;
        }

        String sql = """
                UPDATE vortexauth_accounts
                SET last_login = ?
                WHERE uuid = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, uuid.toString());

            return statement.executeUpdate() == 1;
        }
    }

    public boolean setPremiumStatus(
            UUID uuid,
            boolean enabled,
            boolean verified
    ) throws SQLException {

        if (uuid == null) {
            return false;
        }

        if (enabled && !verified) {
            return false;
        }

        String sql = """
                UPDATE vortexauth_accounts
                SET premium_enabled = ?,
                    premium_verified = ?
                WHERE uuid = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setBoolean(1, enabled);
            statement.setBoolean(2, verified);
            statement.setString(3, uuid.toString());

            return statement.executeUpdate() == 1;
        }
    }

    public boolean changePassword(
            UUID uuid,
            String oldPassword,
            String newPassword
    ) throws SQLException {

        if (uuid == null ||
                !passwordManager.isValidPassword(newPassword)) {
            return false;
        }

        Optional<Account> optionalAccount = getAccount(uuid);

        if (optionalAccount.isEmpty()) {
            return false;
        }

        Account account = optionalAccount.get();

        if (!passwordManager.verifyPassword(
                oldPassword,
                account.passwordHash()
        )) {
            return false;
        }

        String newHash =
                passwordManager.hashPassword(newPassword);

        String sql = """
                UPDATE vortexauth_accounts
                SET password_hash = ?
                WHERE uuid = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, newHash);
            statement.setString(2, uuid.toString());

            return statement.executeUpdate() == 1;
        }
    }

    private Account mapAccount(ResultSet result)
            throws SQLException {

        return new Account(
                UUID.fromString(result.getString("uuid")),
                result.getString("username"),
                result.getString("password_hash"),
                result.getString("platform"),
                result.getString("xuid"),
                result.getBoolean("premium_enabled"),
                result.getBoolean("premium_verified"),
                result.getLong("created_at"),
                result.getLong("last_login")
        );
    }

    public record Account(
            UUID uuid,
            String username,
            String passwordHash,
            String platform,
            String xuid,
            boolean premiumEnabled,
            boolean premiumVerified,
            long createdAt,
            long lastLogin
    ) {
        public boolean isPremium() {
            return premiumEnabled && premiumVerified;
        }

        public boolean isBedrock() {
            return "BEDROCK".equalsIgnoreCase(platform);
        }
    }
}
