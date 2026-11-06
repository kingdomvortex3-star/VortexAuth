
package id.vortexauth.utils;

import id.vortexauth.VortexAuth;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AuthMessageManager {

    private final VortexAuth plugin;

    public AuthMessageManager(VortexAuth plugin) {
        this.plugin = plugin;
    }

    public String get(String key) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bVortexAuth&8] "
        );

        String message = plugin.getConfig().getString(
                "messages." + key,
                "&cMessage not found: " + key
        );

        return color(prefix + message);
    }

    public String getRaw(String key) {
        return color(plugin.getConfig().getString(
                "messages." + key,
                "&cMessage not found: " + key
        ));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public void send(Player player, String key) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendMessage(get(key));
    }

    public void send(
            CommandSender sender,
            String key,
            String placeholder,
            String replacement
    ) {
        String message = get(key).replace(
                placeholder,
                replacement == null ? "" : replacement
        );

        sender.sendMessage(message);
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                message == null ? "" : message
        );
    }

    public void sendPrefix(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bVortexAuth&8] "
        );

        sender.sendMessage(color(prefix + message));
    }
}
