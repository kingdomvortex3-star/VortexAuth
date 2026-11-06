
package id.vortexauth.bedrock;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AccountManager.Account;
import id.vortexauth.utils.SecurityUtils;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.sql.SQLException;
import java.util.Optional;
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

        try {
            return floodgateApi.isFloodgatePlayer(
                    player.getUniqueId()
            );
        } catch (Exception exception) {
            return false;
        }
    }

    public String getXuid(Player player) {
        if (!isBedrock(player)) {
            return null;
        }

        try {
            var floodgatePlayer = floodgateApi.getPlayer(
                    player.getUniqueId()
            );

            if (floodgatePlayer == null) {
                return null;
            }

            String xuid = floodgatePlayer.getXuid();

            return SecurityUtils.isValidXuid(xuid)
                    ? xuid
                    : null;

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

        Optional<Account> account =
                accountManager.getAccountByXuid(xuid);

        return account.orElse(null);
    }

    public boolean isValidXuid(String xuid) {
        return SecurityUtils.isValidXuid(xuid);
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
