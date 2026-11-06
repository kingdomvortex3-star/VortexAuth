
package id.vortexconverter;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class VortexConverter extends JavaPlugin {

    private static VortexConverter instance;

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Failed to create plugin folder!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        getLogger().info("==================================");
        getLogger().info(" VortexConverter is starting...");
        getLogger().info(" Version: " + getPluginMeta().getVersion());
        getLogger().info("==================================");

        getLogger().info("Plugin folder: " + getDataFolder().getPath());
        getLogger().info("Configuration loaded.");

        getLogger().info("VortexConverter enabled successfully!");
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
        File folder = new File(getDataFolder(), "workspace");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }

    public File getOutputFolder() {
        File folder = new File(getDataFolder(), "output");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }
}
