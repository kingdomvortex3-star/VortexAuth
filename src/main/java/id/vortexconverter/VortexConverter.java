
package id.vortexconverter;

import id.vortexconverter.commands.CommandManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VortexConverter extends JavaPlugin {

    private static VortexConverter instance;

    private CommandManager commandManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting VortexConverter...");

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Cannot create plugin directory!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        if (!createFolder("workspace")
                || !createFolder("output")
                || !createFolder("temp")) {
            getLogger().severe("Failed to create required directories!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();

        getLogger().info("VortexConverter initialized.");
        getLogger().info("Java Version: " + System.getProperty("java.version"));
        getLogger().info("Workspace: " + getWorkspaceFolder().getPath());
        getLogger().info("Output: " + getOutputFolder().getPath());
        getLogger().info("Commands registered successfully.");
    }

    private boolean createFolder(String name) {
        File folder = new File(getDataFolder(), name);

        if (folder.exists()) {
            return folder.isDirectory();
        }

        return folder.mkdirs();
    }

    private void registerCommands() {
        commandManager = new CommandManager(this);

        PluginCommand command = getCommand("vortexconvert");

        if (command == null) {
            getLogger().severe(
                    "Command vortexconvert is missing from plugin.yml!"
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        command.setExecutor(commandManager);
        command.setTabCompleter(commandManager);
    }

    @Override
    public void onDisable() {
        getLogger().info("VortexConverter disabled.");
        instance = null;
    }

    public static VortexConverter getInstance() {
        return instance;
    }

    public File getWorkspaceFolder() {
        return new File(getDataFolder(), "workspace");
    }

    public File getOutputFolder() {
        return new File(getDataFolder(), "output");
    }

    public File getTempFolder() {
        return new File(getDataFolder(), "temp");
    }
}
