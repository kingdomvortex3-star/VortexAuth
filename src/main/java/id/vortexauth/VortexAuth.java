
package id.vortexauth;

import org.bukkit.plugin.java.JavaPlugin;

public final class VortexAuth extends JavaPlugin {

    private static VortexAuth instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("================================");
        getLogger().info("       VortexAuth v1.0.0");
        getLogger().info("================================");
        getLogger().info("Initializing authentication engine...");

        getLogger().info("Configuration loaded.");
        getLogger().info("VortexAuth has been enabled!");
        getLogger().info("================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down VortexAuth...");

        instance = null;

        getLogger().info("VortexAuth has been disabled.");
    }

    public static VortexAuth getInstance() {
        return instance;
    }
}
