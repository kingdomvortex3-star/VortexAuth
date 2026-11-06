
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;

public final class PlayerChatListener implements Listener {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public PlayerChatListener(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    private boolean isProtected(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        )) {
            return false;
        }

        if (player.hasPermission("vortexauth.bypass")) {
            return false;
        }

        return !authenticationManager.isAuthenticated(
                player.getUniqueId()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-chat", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);

            event.getPlayer().sendMessage(
                    color(
                            plugin.getConfig().getString(
                                    "messages.prefix",
                                    "&8[&bVortexAuth&8] "
                            )
                            + plugin.getConfig().getString(
                                    "messages.login-required",
                                    "&cPlease login first!"
                            )
                    )
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean(
                "authentication.commands.block-before-login",
                true
        )) {
            return;
        }

        Player player = event.getPlayer();

        if (!isProtected(player)) {
            return;
        }

        String message = event.getMessage();

        if (message == null || !message.startsWith("/")) {
            return;
        }

        String[] parts = message.substring(1).split("\\s+", 2);

        if (parts.length == 0 || parts[0].isBlank()) {
            event.setCancelled(true);
            return;
        }

        String command = parts[0].toLowerCase(Locale.ROOT);

        if (command.contains(":")) {
            command = command.substring(
                    command.lastIndexOf(':') + 1
            );
        }

        List<String> allowed = plugin.getConfig().getStringList(
                "authentication.commands.allowed"
        );

        boolean permitted = allowed.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(command::equals);

        if (!permitted) {
            event.setCancelled(true);

            player.sendMessage(
                    color(
                            plugin.getConfig().getString(
                                    "messages.prefix",
                                    "&8[&bVortexAuth&8] "
                            )
                            + plugin.getConfig().getString(
                                    "messages.login-required",
                                    "&cPlease login first!"
                            )
                    )
            );
        }
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                message
        );
    }
}
