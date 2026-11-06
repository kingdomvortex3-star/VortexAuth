
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthResult;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            sendRaw(player, "&aAuthentication bypass is enabled.");
            return true;
        }

        if (args.length != 1) {
            sendRaw(player, "&cUsage: /login <password>");
            return true;
        }

        boolean ipCheck = plugin.getConfig().getBoolean(
                "session.ip-check", true
        );

        String ipHash = null;

        if (ipCheck) {
            if (plugin.getIpAddressManager() == null) {
                sendRaw(player, "&cAuthentication security is unavailable.");
                return true;
            }

            ipHash = plugin.getIpAddressManager()
                    .getPlayerIpHash(player);

            if (ipHash == null || ipHash.isBlank()) {
                sendRaw(player, "&cUnable to verify your connection.");
                return true;
            }
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId(),
                ipHash
        )) {
            sendRaw(player, "&aYou are already logged in!");
            return true;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            authenticationManager.invalidateSession(
                    player.getUniqueId()
            );
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
            send(player, "messages.too-many-attempts");
            return true;
        }

        AuthResult result = authenticationManager.login(
                player.getUniqueId(),
                args[0],
                ipHash
        );

        handleResult(player, result, ipHash);

        return true;
    }

    private void handleResult(
            Player player,
            AuthResult result,
            String ipHash
    ) {
        switch (result) {
            case SUCCESS -> {
                if (!authenticationManager.isAuthenticated(
                        player.getUniqueId(), ipHash
                )) {
                    authenticationManager.invalidateSession(
                            player.getUniqueId()
                    );

                    sendRaw(
                            player,
                            "&cFailed to create authentication session."
                    );
                    return;
                }

                send(player, "messages.login-success");
                sendRaw(
                        player,
                        "&7Your authentication session is now active."
                );
            }

            case INVALID_PASSWORD ->
                    send(player, "messages.invalid-password");

            case TOO_MANY_ATTEMPTS, ACCOUNT_LOCKED ->
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
