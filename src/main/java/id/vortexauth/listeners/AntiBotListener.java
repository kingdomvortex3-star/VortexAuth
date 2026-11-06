
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.security.AntiBotManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AntiBotListener implements Listener {

    private final VortexAuth plugin;
    private final AntiBotManager antiBotManager;

    public AntiBotListener(
            VortexAuth plugin,
            AntiBotManager antiBotManager
    ) {
        this.plugin = plugin;
        this.antiBotManager = antiBotManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!antiBotManager.checkJoin(player)) {
            String message = plugin.getConfig().getString(
                    "messages.bot-blocked",
                    "&cKoneksi kamu terdeteksi sebagai bot!"
            );

            player.kick(
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            message
                    )
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        antiBotManager.handleQuit(event.getPlayer());
    }
}
