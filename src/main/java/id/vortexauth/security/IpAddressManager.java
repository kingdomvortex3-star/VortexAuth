
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public PlayerConnectionListener(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getBoolean(
                "authentication.enabled",
                true
        )) {
            return;
        }

        if (isValidSession(player)) {
            return;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            authenticationManager.invalidateSession(
                    player.getUniqueId()
            );
        }

        player.sendMessage("");

        if (authenticationManager.isRegistered(
                player.getUniqueId()
        )) {
            sendMessage(player, "messages.login-required");
        } else {
            sendMessage(player, "messages.register-required");
        }

        player.sendMessage("");
    }

    private boolean isValidSession(Player player) {
        if (!plugin.getConfig().getBoolean(
                "session.enabled",
                true
        )) {
            return false;
        }

        if (plugin.getIpAddressManager() == null) {
            return false;
        }

        String ipHash = plugin.getIpAddressManager()
                .getPlayerIpHash(player);

        return authenticationManager.isAuthenticated(
                player.getUniqueId(),
                ipHash
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Session tidak dihapus saat pemain disconnect.
        // Session tetap berlaku sampai kedaluwarsa.
    }

    private void sendMessage(Player player, String path) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bVortexAuth&8] "
        );

        String message = plugin.getConfig().getString(
                path,
                "&cAuthentication required."
        );

        player.sendMessage(
                ChatColor.translateAlternateColorCodes(
                        '&',
                        prefix + message
                )
        );
    }
}
