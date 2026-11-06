
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
                "authentication.enabled", true
        )) {
            return;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            return;
        }

        player.sendMessage("");

        if (authenticationManager.isRegistered(
                player.getUniqueId()
        )) {
            sendMessage(
                    player,
                    "messages.login-required"
            );
        } else {
            sendMessage(
                    player,
                    "messages.register-required"
            );
        }

        player.sendMessage("");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        authenticationManager.logout(
                player.getUniqueId()
        );
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

        player.sendMessage(color(prefix + message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                message
        );
    }
}
