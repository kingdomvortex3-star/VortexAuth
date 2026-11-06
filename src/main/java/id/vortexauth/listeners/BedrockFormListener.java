
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.bedrock.BedrockFormManager;
import id.vortexauth.bedrock.FloodgateManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;

public final class BedrockFormListener implements Listener {

    private final VortexAuth plugin;
    private final FloodgateManager floodgateManager;
    private final BedrockFormManager formManager;
    private final AccountManager accountManager;
    private final AuthenticationManager authenticationManager;

    public BedrockFormListener(
            VortexAuth plugin,
            FloodgateManager floodgateManager,
            BedrockFormManager formManager,
            AccountManager accountManager,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.floodgateManager = floodgateManager;
        this.formManager = formManager;
        this.accountManager = accountManager;
        this.authenticationManager = authenticationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getBoolean(
                "bedrock.enabled", true
        )) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "bedrock.ui.enabled", true
        )) {
            return;
        }

        if (!floodgateManager.isBedrock(player)) {
            return;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            return;
        }

        player.getScheduler().runDelayed(
                plugin,
                task -> openAppropriateForm(player),
                () -> {},
                20L
        );
    }

    private void openAppropriateForm(Player player) {
        if (!player.isOnline()) {
            return;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            return;
        }

        try {
            String xuid = floodgateManager.getXuid(player);

            if (xuid == null) {
                player.kickPlayer(
                        ChatColor.RED + "Invalid Floodgate identity."
                );
                return;
            }

            boolean linked = accountManager
                    .getAccountByXuid(xuid)
                    .isPresent();

            if (!linked) {
                player.sendMessage(
                        ChatColor.RED
                                + "Akun Bedrock kamu belum terhubung."
                );

                player.sendMessage(
                        ChatColor.GRAY
                                + "Hubungi administrator untuk linking akun."
                );

                return;
            }

            formManager.showLoginForm(player);

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to load Bedrock account: "
                            + exception.getMessage()
            );

            player.sendMessage(
                    ChatColor.RED
                            + "Database error. Please contact an administrator."
            );
        }
    }
}
