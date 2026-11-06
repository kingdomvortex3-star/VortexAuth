
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LogoutCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public LogoutCommand(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.player-only");
            return true;
        }

        if (player.hasPermission("vortexauth.bypass")) {
            sendRaw(player, "&cLogout is unavailable for bypass players.");
            return true;
        }

        boolean loggedOut = authenticationManager.logout(
                player.getUniqueId()
        );

        if (!loggedOut) {
            sendRaw(player, "&cYou are not currently logged in!");
            return true;
        }

        sendRaw(
                player,
                "&aYou have successfully logged out!"
        );

        send(
                player,
                "messages.login-required"
        );

        return true;
    }

    private void send(CommandSender sender, String path) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bVortexAuth&8] "
        );

        String message = plugin.getConfig().getString(
                path,
                "&cAuthentication required."
        );

        sender.sendMessage(color(prefix + message));
    }

    private void sendRaw(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bVortexAuth&8] "
        );

        sender.sendMessage(color(prefix + message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
