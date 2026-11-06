
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class AuthAdminCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final AuthenticationManager authenticationManager;

    public AuthAdminCommand(
            VortexAuth plugin,
            AccountManager accountManager,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("vortexauth.admin")) {
            sender.sendMessage(color("&cKamu tidak memiliki izin."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> reload(sender);
            case "status", "info" -> showStatus(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadConfig();

            sender.sendMessage(color(
                    "&8[&bVortexAuth&8] &aKonfigurasi berhasil dimuat ulang!"
            ));
        } catch (Exception exception) {
            sender.sendMessage(color(
                    "&8[&bVortexAuth&8] &cGagal memuat ulang konfigurasi."
            ));

            plugin.getLogger().severe(
                    "Gagal reload konfigurasi: " + exception.getMessage()
            );
        }
    }

    private void showStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(
                    "&cGunakan: /authadmin status <player>"
            ));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        Optional<AccountManager.Account> account =
                accountManager.getAccount(target.getUniqueId());

        String playerName = target.getName() != null
                ? target.getName()
                : args[1];

        sender.sendMessage(color("&8&m-----------------------------"));
        sender.sendMessage(color("&b&lVortexAuth &7- Account Status"));
        sender.sendMessage(color("&7Player: &f" + playerName));

        if (account.isEmpty()) {
            sender.sendMessage(color("&7Account: &cBelum terdaftar"));
            sender.sendMessage(color("&7Authentication: &cTidak tersedia"));
        } else {
            sender.sendMessage(color("&7Account: &aTerdaftar"));

            boolean authenticated =
                    authenticationManager.isAuthenticated(
                            target.getUniqueId()
                    );

            sender.sendMessage(
                    color("&7Authentication: "
                            + (authenticated
                            ? "&aAuthenticated"
                            : "&cUnauthenticated"))
            );
        }

        sender.sendMessage(color("&8&m-----------------------------"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m-----------------------------"));
        sender.sendMessage(color("&b&lVortexAuth &7- Admin Commands"));
        sender.sendMessage(color("&b/authadmin reload &7- Reload config"));
        sender.sendMessage(color("&b/authadmin status <player> &7- Check account"));
        sender.sendMessage(color("&8&m-----------------------------"));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
