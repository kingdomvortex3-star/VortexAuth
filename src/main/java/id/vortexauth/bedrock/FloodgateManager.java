
package id.vortexauth.bedrock;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AccountManager.Account;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.sql.SQLException;
import java.util.UUID;

public final class FloodgateManager {

    private final VortexAuth plugin;
    private final AccountManager accountManager;

    private FloodgateApi floodgateApi;

    public FloodgateManager(
            VortexAuth plugin,
            AccountManager accountManager
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;

        if (plugin.getServer().getPluginManager()
                .getPlugin("floodgate") != null) {
            try {
                floodgateApi = FloodgateApi.getInstance();
            } catch (Exception exception) {
                plugin.getLogger().warning(
                        "Unable to initialize Floodgate API: "
                                + exception.getMessage()
                );
            }
        }
    }

    public boolean isAvailable() {
        return floodgateApi != null;
    }

    public boolean isBedrock(Player player) {
        if (player == null || floodgateApi == null) {
            return false;
        }

        return floodgateApi.isFloodgatePlayer(
                player.getUniqueId()
        );
    }

    public String getXuid(Player player) {
        if (!isBedrock(player)) {
            return null;
        }

        try {
            String xuid = floodgateApi
                    .getPlayer(player.getUniqueId())
                    .getXuid();

            if (!isValidXuid(xuid)) {
                return null;
            }

            return xuid;

        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Failed to retrieve Floodgate XUID."
            );
            return null;
        }
    }

    public Account getAccount(Player player) throws SQLException {
        String xuid = getXuid(player);

        if (xuid == null) {
            return null;
        }

        return accountManager.getAccountByXuid(xuid);
    }

    public boolean isValidXuid(String xuid) {
        if (xuid == null || xuid.isBlank()) {
            return false;
        }

        return xuid.matches("[0-9]{1,20}");
    }

    public boolean hasLinkedAccount(Player player) throws SQLException {
        return getAccount(player) != null;
    }

    public UUID getBedrockUuid(Player player) {
        if (!isBedrock(player)) {
            return null;
        }

        return player.getUniqueId();
    }
}
