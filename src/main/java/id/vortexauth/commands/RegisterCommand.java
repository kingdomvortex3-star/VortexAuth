
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthResult;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RegisterCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public RegisterCommand(
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
                "authentication.register.enabled", true
        )) {
            sendRaw(player, "&cRegistration is currently disabled.");
            return true;
        }

        if (player.hasPermission("vortexauth.bypass")) {
            sendRaw(player, "&cYou cannot register using bypass.");
            return true;
        }

        if (authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            sendRaw(player, "&cYou are already logged in!");
            return true;
        }

        boolean confirmationRequired =
                plugin.getConfig().getBoolean(
                        "authentication.register.require-confirmation",
                        true
                );

        int requiredArguments = confirmationRequired ? 2 : 1;

        if (args.length != requiredArguments) {
            if (confirmationRequired) {
                player.sendMessage(color(
                        "&cUsage: /register <password> <confirm-password>"
                ));
            } else {
                player.sendMessage(color(
                        "&cUsage: /register <password>"
                ));
            }

            return true;
        }

        String password = args[0];

        String confirmation = confirmationRequired
                ? args[1]
                : password;

        AuthResult result = authenticationManager.register(
                player.getUniqueId(),
                player.getName(),
                password,
                confirmation,
                "JAVA",
                null
        );

        handleResult(player, result);

        return true;
    }

    private void handleResult(Player player, AuthResult result) {
        switch (result) {
            case SUCCESS -> {
                send(player, "messages.register-success");
                sendRaw(
                        player,
                        "&7Silakan login menggunakan &b/login <password>"
                );
            }

            case ALREADY_REGISTERED ->
                    send(player, "messages.already-registered");

            case INVALID_USERNAME ->
                    sendRaw(player, "&cUsername tidak valid!");

            case PASSWORD_TOO_SHORT ->
                    send(player, "messages.password-too-short");

            case PASSWORD_TOO_LONG ->
                    sendRaw(player, "&cPassword terlalu panjang!");

            case PASSWORD_MISMATCH ->
                    send(player, "messages.password-mismatch");

            case INVALID_PASSWORD ->
                    send(player, "messages.invalid-password");

            case DATABASE_ERROR ->
                    sendRaw(player, "&cTerjadi kesalahan database!");

            case INTERNAL_ERROR ->
                    sendRaw(player, "&cTerjadi kesalahan internal!");

            default ->
                    sendRaw(player, "&cRegistrasi gagal: " + result.name());
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
