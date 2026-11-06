
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.database.DatabaseManager;
import id.vortexauth.security.SecurityUtils;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class AccountManager {

    private final VortexAuth plugin;
    private final DatabaseManager databaseManager;
    private final PasswordManager passwordManager;

    public AccountManager(
            VortexAuth plugin,
            DatabaseManager databaseManager,
            PasswordManager passwordManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.passwordManager = passwordManager;
    }

    public boolean register(
            UUID uuid,
            String username,
            String password,
            String platform,
            String xuid
    ) throws SQLException {

        if (uuid == null
                || username == null
                || password == null
                || platform == null) {
            return false;
        }

        String normalizedPlatform =
                platform.toUpperCase(Locale.ROOT);

        if (!normalizedPlatform.equals("JAVA")
                && !normalizedPlatform.equals("BEDROCK")) {
            return false;
        }

        if (!SecurityUtils.isValidUsername(username)) {
            return false;
        }

        if (!isPasswordValid(password)) {
            return false;
        }

        if (normalizedPlatform.equals("BEDROCK")) {
            if (!SecurityUtils.isValidXuid(xuid)) {
                return false;
            }
        } else {
            xuid = null;
        }

        if (exists(uuid)) {
            return false;
        }

        if (getAccountByUsername(username).isPresent()) {
            return false;
        }

        if (xuid != null && getAccountByXuid(xuid).isPresent()) {
            return false;
        }

        int strength = plugin.getConfig().getInt(
                "authentication.password.bcrypt-strength",
                12
        );

        strength = Math.max(10, Math.min(14, strength));

        String passwordHash = BCrypt.hashpw(
                password,
                BCrypt.gensalt(strength)
        );

        String sql = """
                INSERT INTO accounts (
                    uuid,
                    username,
                    password_hash,
                    platform,
                    xuid,
                    premium_enabled,
                    premium_verified,
                    created_at,
                    last_login
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long now = System.currentTimeMillis();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, passwordHash);
            statement.setString(4, normalizedPlatform);
            statement.setString(5, xuid);
            statement.setBoolean(6, false);
            statement.setBoolean(7, false);
            statement.setLong(8, now);
            statement.setLong(9, 0L);

            return statement.executeUpdate() == 1;
        }
    }

    public boolean login(
            UUID uuid,
            String password
    ) throws SQLException {

        if (uuid == null || password == null) {
            return false;
        }

        Optional<Account> optionalAccount = getAccount(uuid);

        if (optionalAccount.isEmpty()) {
            return false;
        }

        Account account = optionalAccount.get();

        boolean valid;

        try {
            valid = BCrypt.checkpw(
                    password,
                    account.passwordHash()
            );
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Invalid password hash for account " + uuid
            );
            return false;
        }

        if (!valid) {
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
                SELECT uuid, username, password_hash, platform,
                       xuid, premium_enabled, premium_verified,
                       created_at, last_login
                FROM accounts
                WHERE uuid = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(readAccount(result));
            }
        }
    }

    public Optional<Account> getAccountByUsername(String username)
            throws SQLException {

        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String sql = """
                SELECT uuid, username, password_hash, platform,
                       xuid, premium_enabled, premium_verified,
                       created_at, last_login
                FROM accounts
                WHERE LOWER(username) = LOWER(?)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(readAccount(result));
            }
        }
    }

    public Optional<Account> getAccountByXuid(String xuid)
            throws SQLException {

        if (!SecurityUtils.isValidXuid(xuid)) {
            return Optional.empty();
        }

        String sql = """
                SELECT uuid, username, password_hash, platform,
                       xuid, premium_enabled, premium_verified,
                       created_at, last_login
                FROM accounts
                WHERE xuid = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, xuid);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(readAccount(result));
            }
        }
    }

    public boolean exists(UUID uuid) throws SQLException {
        if (uuid == null) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM accounts
                WHERE uuid = ?
                LIMIT 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void updateLastLogin(UUID uuid) throws SQLException {
        if (uuid == null) {
            return;
        }

        String sql = """
                UPDATE accounts
                SET last_login = ?
                WHERE uuid = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void setPremiumStatus(
            UUID uuid,
            boolean enabled,
            boolean verified
    ) throws SQLException {

        if (uuid == null) {
            return;
        }

        String sql = """
                UPDATE accounts
                SET premium_enabled = ?,
                    premium_verified = ?
                WHERE uuid = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setBoolean(1, enabled);
            statement.setBoolean(2, verified);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    public boolean changePassword(
            UUID uuid,
            String oldPassword,
            String newPassword
    ) throws SQLException {

        if (uuid == null
                || oldPassword == null
                || newPassword == null) {
            return false;
        }

        Optional<Account> optionalAccount = getAccount(uuid);

        if (optionalAccount.isEmpty()) {
            return false;
        }

        Account account = optionalAccount.get();

        boolean oldPasswordValid;

        try {
            oldPasswordValid = BCrypt.checkpw(
                    oldPassword,
                    account.passwordHash()
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (!oldPasswordValid || !isPasswordValid(newPassword)) {
            return false;
        }

        int strength = plugin.getConfig().getInt(
                "authentication.password.bcrypt-strength",
                12
        );

        strength = Math.max(10, Math.min(14, strength));

        String newHash = BCrypt.hashpw(
                newPassword,
                BCrypt.gensalt(strength)
        );

        String sql = """
                UPDATE accounts
                SET password_hash = ?
                WHERE uuid = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, newHash);
            statement.setString(2, uuid.toString());

            return statement.executeUpdate() == 1;
        }
    }

    private boolean isPasswordValid(String password) {
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

        int length = password.length();
        int byteLength = password.getBytes(
                StandardCharsets.UTF_8
        ).length;

        return length >= minimum
                && length <= maximum
                && byteLength <= 72;
    }

    private Account readAccount(ResultSet result)
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
