
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.security.AntiBotManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getBoolean(
                "anti-bot.enabled", true
        )) {
            return;
        }

        if (!antiBotManager.checkJoin(player)) {
            String message = plugin.getConfig().getString(
                    "messages.bot-blocked",
                    "&cKoneksi kamu terdeteksi sebagai bot!"
            );

            Component kickMessage =
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(message);

            player.kick(kickMessage);

            if (plugin.getDiscordWebhookManager() != null) {
                plugin.getDiscordWebhookManager()
                        .sendBotDetected(player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        antiBotManager.handleQuit(event.getPlayer());
    }
}
