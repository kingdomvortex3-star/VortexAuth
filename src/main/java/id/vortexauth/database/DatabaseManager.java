
package id.vortexauth.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import id.vortexauth.VortexAuth;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final VortexAuth plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(VortexAuth plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        String type = plugin.getConfig()
                .getString("database.type", "SQLITE")
                .toUpperCase();

        HikariConfig config = new HikariConfig();

        if (type.equals("MYSQL")) {
            configureMySQL(config);
        } else {
            configureSQLite(config);
        }

        int maximumPool = plugin.getConfig()
                .getInt("database.pool.maximum-size", 10);

        int minimumIdle = plugin.getConfig()
                .getInt("database.pool.minimum-idle", 2);

        int connectionTimeout = plugin.getConfig()
                .getInt("database.pool.connection-timeout", 30000);

        config.setMaximumPoolSize(Math.max(1, maximumPool));
        config.setMinimumIdle(Math.max(0,
                Math.min(minimumIdle, maximumPool)));

        config.setConnectionTimeout(
                Math.max(250, connectionTimeout)
        );

        config.setPoolName("VortexAuth-Database");

        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);

        createTables();

        plugin.getLogger().info(
                "Database initialized successfully!"
        );
    }

    private void configureSQLite(HikariConfig config) {
        String fileName = plugin.getConfig()
                .getString("database.sqlite.file", "data.db");

        File databaseFile = new File(
                plugin.getDataFolder(),
                fileName
        );

        File parent = databaseFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        config.setDriverClassName("org.sqlite.JDBC");

        config.setJdbcUrl(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath()
        );

        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
    }

    private void configureMySQL(HikariConfig config) {
        String host = plugin.getConfig()
                .getString("database.mysql.host", "localhost");

        int port = plugin.getConfig()
                .getInt("database.mysql.port", 3306);

        String database = plugin.getConfig()
                .getString("database.mysql.database", "vortexauth");

        String username = plugin.getConfig()
                .getString("database.mysql.username", "root");

        String password = plugin.getConfig()
                .getString("database.mysql.password", "");

        boolean ssl = plugin.getConfig()
                .getBoolean("database.mysql.ssl", false);

        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        config.setJdbcUrl(
                "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=" + ssl
                        + "&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC"
        );

        config.setUsername(username);
        config.setPassword(password);
    }

    private void createTables() throws SQLException {
        String accounts = """
                CREATE TABLE IF NOT EXISTS vortexauth_accounts (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(64) NOT NULL,
                    password_hash VARCHAR(255),
                    platform VARCHAR(16) NOT NULL DEFAULT 'JAVA',
                    xuid VARCHAR(32),
                    premium_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    premium_verified BOOLEAN NOT NULL DEFAULT FALSE,
                    last_ip_hash VARCHAR(128),
                    created_at BIGINT NOT NULL,
                    last_login BIGINT NOT NULL DEFAULT 0
                )
                """;

        String sessions = """
                CREATE TABLE IF NOT EXISTS vortexauth_sessions (
                    uuid VARCHAR(36) PRIMARY KEY,
                    session_token VARCHAR(128) NOT NULL,
                    ip_hash VARCHAR(128),
                    expires_at BIGINT NOT NULL
                )
                """;

        String security = """
                CREATE TABLE IF NOT EXISTS vortexauth_security (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    event_type VARCHAR(64) NOT NULL,
                    details VARCHAR(512),
                    created_at BIGINT NOT NULL
                )
                """;

        String securitySQLite = """
                CREATE TABLE IF NOT EXISTS vortexauth_security (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    event_type VARCHAR(64) NOT NULL,
                    details VARCHAR(512),
                    created_at BIGINT NOT NULL
                )
                """;

        String type = plugin.getConfig()
                .getString("database.type", "SQLITE");

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(accounts);
            statement.execute(sessions);

            statement.execute(
                    type.equalsIgnoreCase("MYSQL")
                            ? security
                            : securitySQLite
            );

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_vortexauth_username "
                            + "ON vortexauth_accounts(username)"
            );

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_vortexauth_xuid "
                            + "ON vortexauth_accounts(xuid)"
            );
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException(
                    "VortexAuth database is not initialized!"
            );
        }

        return dataSource.getConnection();
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info(
                    "Database connection pool closed."
            );
        }
    }
}
