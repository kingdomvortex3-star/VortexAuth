
package id.vortexauth;

import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.auth.AuthResult;
import id.vortexauth.auth.LoginAttemptManager;
import id.vortexauth.auth.PasswordManager;
import id.vortexauth.auth.RegistrationManager;
import id.vortexauth.session.SessionManager;

import id.vortexauth.bedrock.BedrockFormManager;
import id.vortexauth.bedrock.FloodgateManager;

import id.vortexauth.commands.AuthAdminCommand;
import id.vortexauth.commands.AuthCommand;
import id.vortexauth.commands.ChangePasswordCommand;
import id.vortexauth.commands.LoginCommand;
import id.vortexauth.commands.LogoutCommand;
import id.vortexauth.commands.RegisterCommand;

import id.vortexauth.database.DatabaseManager;

import id.vortexauth.discord.DiscordWebhookManager;

import id.vortexauth.listeners.AntiBotListener;
import id.vortexauth.listeners.AntiVpnListener;
import id.vortexauth.listeners.BedrockFormListener;
import id.vortexauth.listeners.PlayerChatListener;
import id.vortexauth.listeners.PlayerConnectionListener;
import id.vortexauth.listeners.PlayerProtectionListener;

import id.vortexauth.security.AntiBotManager;
import id.vortexauth.security.AntiVpnManager;
import id.vortexauth.security.IpAddressManager;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;

public final class VortexAuth extends JavaPlugin {

    private static VortexAuth instance;

    private DatabaseManager databaseManager;
    private PasswordManager passwordManager;
    private AccountManager accountManager;
    private RegistrationManager registrationManager;
    private LoginAttemptManager loginAttemptManager;
    private SessionManager sessionManager;
    private AuthenticationManager authenticationManager;

    private IpAddressManager ipAddressManager;
    private AntiBotManager antiBotManager;
    private AntiVpnManager antiVpnManager;
    private DiscordWebhookManager discordWebhookManager;

    private FloodgateManager floodgateManager;
    private BedrockFormManager bedrockFormManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getLogger().info("================================");
        getLogger().info("       VortexAuth v1.0.0");
        getLogger().info("================================");

        try {
            initializeDatabase();
            initializeAuthentication();
            initializeSecurity();
            initializeBedrock();
            registerCommands();
            registerListeners();

            getLogger().info("VortexAuth initialized successfully!");
            getLogger().info("================================");

        } catch (Exception exception) {
            getLogger().severe("VortexAuth initialization failed!");
            exception.printStackTrace();

            shutdownManagers();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeDatabase() throws SQLException {
        getLogger().info("Initializing database...");
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
    }

    private void initializeAuthentication() {
        getLogger().info("Initializing authentication engine...");

        passwordManager = new PasswordManager(this);

        accountManager = new AccountManager(
                this,
                databaseManager,
                passwordManager
        );

        registrationManager = new RegistrationManager(
                this,
                accountManager,
                passwordManager
        );

        loginAttemptManager = new LoginAttemptManager(this);
        sessionManager = new SessionManager(this);

        authenticationManager = new AuthenticationManager(
                this,
                accountManager,
                registrationManager,
                loginAttemptManager,
                sessionManager
        );
    }

    private void initializeSecurity() throws IOException {
        getLogger().info("Initializing security systems...");

        ipAddressManager = new IpAddressManager(this);

        antiBotManager = new AntiBotManager(
                this,
                ipAddressManager
        );

        antiVpnManager = new AntiVpnManager(this);
        discordWebhookManager = new DiscordWebhookManager(this);
    }

    private void initializeBedrock() {
        getLogger().info("Initializing Bedrock integration...");

        floodgateManager = new FloodgateManager(
                this,
                accountManager
        );

        if (!floodgateManager.isAvailable()) {
            getLogger().warning(
                    "Floodgate not found. Bedrock authentication is unavailable."
            );
            return;
        }

        bedrockFormManager = new BedrockFormManager(
                this,
                floodgateManager,
                new BedrockFormManager.AuthFormHandler() {

                    @Override
                    public void onLogin(Player player, String password) {
                        if (player == null || !player.isOnline()) {
                            return;
                        }

                        String ipHash =
                                ipAddressManager.getPlayerIpHash(player);

                        AuthResult result = authenticationManager.login(
                                player.getUniqueId(),
                                password,
                                ipHash
                        );

                        if (result == AuthResult.SUCCESS) {
                            player.sendMessage(color(
                                    "&aLogin berhasil! Selamat datang."
                            ));
                        } else {
                            player.sendMessage(color(
                                    "&cLogin gagal. Periksa password dan coba lagi."
                            ));
                        }
                    }

                    @Override
                    public void onRegister(
                            Player player,
                            String password,
                            String confirmation
                    ) {
                        if (player == null || !player.isOnline()) {
                            return;
                        }

                        player.sendMessage(color(
                                "&cRegistrasi Bedrock belum tersedia."
                        ));
                        player.sendMessage(color(
                                "&7Silakan hubungi admin untuk menautkan akun."
                        ));
                    }
                }
        );

        getLogger().info("Floodgate integration initialized.");
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        registerCommand(
                "register",
                new RegisterCommand(this, authenticationManager)
        );

        registerCommand(
                "login",
                new LoginCommand(this, authenticationManager)
        );

        registerCommand(
                "logout",
                new LogoutCommand(this, authenticationManager)
        );

        registerCommand(
                "changepassword",
                new ChangePasswordCommand(
                        this,
                        accountManager,
                        authenticationManager
                )
        );

        registerCommand(
                "auth",
                new AuthCommand(
                        this,
                        accountManager,
                        authenticationManager
                )
        );

        registerCommand(
                "authadmin",
                new AuthAdminCommand(
                        this,
                        accountManager,
                        authenticationManager
                )
        );
    }

    private void registerCommand(
            String name,
            CommandExecutor executor
    ) {
        PluginCommand command = getCommand(name);

        if (command == null) {
            throw new IllegalStateException(
                    "Command '" + name
                            + "' is missing from plugin.yml!"
            );
        }

        command.setExecutor(executor);
    }

    private void registerListeners() {
        getLogger().info("Registering listeners...");

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(
                        this,
                        authenticationManager
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new PlayerProtectionListener(
                        this,
                        authenticationManager
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new PlayerChatListener(
                        this,
                        authenticationManager
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new AntiBotListener(
                        this,
                        antiBotManager
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new AntiVpnListener(
                        this,
                        antiVpnManager
                ),
                this
        );

        if (floodgateManager != null
                && floodgateManager.isAvailable()
                && bedrockFormManager != null) {

            getServer().getPluginManager().registerEvents(
                    new BedrockFormListener(
                            this,
                            accountManager,
                            authenticationManager,
                            floodgateManager,
                            bedrockFormManager
                    ),
                    this
            );
        }
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void shutdownManagers() {
        if (authenticationManager != null) {
            authenticationManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down VortexAuth...");
        shutdownManagers();
        instance = null;
        getLogger().info("VortexAuth disabled.");
    }

    public static VortexAuth getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PasswordManager getPasswordManager() {
        return passwordManager;
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    public LoginAttemptManager getLoginAttemptManager() {
        return loginAttemptManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    public IpAddressManager getIpAddressManager() {
        return ipAddressManager;
    }

    public AntiBotManager getAntiBotManager() {
        return antiBotManager;
    }

    public AntiVpnManager getAntiVpnManager() {
        return antiVpnManager;
    }

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public FloodgateManager getFloodgateManager() {
        return floodgateManager;
    }

    public BedrockFormManager getBedrockFormManager() {
        return bedrockFormManager;
    }
}
