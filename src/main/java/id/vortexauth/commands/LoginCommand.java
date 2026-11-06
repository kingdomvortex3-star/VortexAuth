
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthResult;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;

public final class LoginCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public LoginCommand(
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

        if (!plugin.getConfig().getBoolean(
                "authentication.login.enabled", true
        )) {
            sendRaw(player, "&cLogin is currently disabled.");
            return true;
        }

        if (player.hasPermission("vortexauth.bypass")) {
            sendRaw(player, "&cYou are using authentication bypass.");
            return true;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            sendRaw(player, "&aYou are already logged in!");
            return true;
        }

        if (!authenticationManager.isRegistered(
                player.getUniqueId()
        )) {
            send(player, "messages.not-registered");
            return true;
        }

        if (authenticationManager.isLocked(
                player.getUniqueId()
        )) {
            long remaining =
                    authenticationManager.getRemainingLockTime(
                            player.getUniqueId()
                    );

            long seconds = (remaining + 999) / 1000;

            sendRaw(
                    player,
                    "&cAccount locked! Try again in &e"
                            + seconds + " &cseconds."
            );

            return true;
        }

        if (args.length != 1) {
            sendRaw(player, "&cUsage: /login <password>");
            return true;
        }

        String ipHash = getIpHash(player);

        AuthResult result = authenticationManager.login(
                player.getUniqueId(),
                args[0],
                ipHash
        );

        handleResult(player, result);

        return true;
    }

    private String getIpHash(Player player) {
        InetSocketAddress address = player.getAddress();

        if (address == null || address.getAddress() == null) {
            return null;
        }

        /*
         * IP hashing will be connected to the
         * persistent secret manager later.
         *
         * Never store raw IP addresses in sessions.
         */
        return null;
    }

    private void handleResult(Player player, AuthResult result) {
        switch (result) {
            case SUCCESS -> {
                send(player, "messages.login-success");

                sendRaw(
                        player,
                        "&7Your authentication session is now active."
                );
            }

            case INVALID_PASSWORD -> {
                send(player, "messages.invalid-password");

                int remaining =
                        authenticationManager.getRemainingAttempts(
                                player.getUniqueId()
                        );

                sendRaw(
                        player,
                        "&7Remaining attempts: &e" + remaining
                );
            }

            case TOO_MANY_ATTEMPTS -> {
                send(player, "messages.too-many-attempts");

                sendRaw(
                        player,
                        "&cYour account has been temporarily locked."
                );
            }

            case ACCOUNT_LOCKED ->
                    send(player, "messages.too-many-attempts");

            case NOT_REGISTERED ->
                    send(player, "messages.not-registered");

            case ALREADY_LOGGED_IN ->
                    sendRaw(player, "&aYou are already logged in!");

            case DATABASE_ERROR ->
                    sendRaw(player, "&cDatabase error during login!");

            case INTERNAL_ERROR ->
                    sendRaw(player, "&cInternal authentication error!");

            default ->
                    sendRaw(
                            player,
                            "&cLogin failed: " + result.name()
                    );
        }
    }

    private void send(CommandSender sender, String path) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bVortexAuth&8] "
        );

        String message = plugin.getConfig().getString(
                path,
                "&cAuthentication error."
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
