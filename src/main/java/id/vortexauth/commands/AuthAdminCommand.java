
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.auth.AccountManager.Account;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

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
            sender.sendMessage(color("&cKamu tidak memiliki izin!"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(color("&aKonfigurasi VortexAuth berhasil dimuat ulang."));
                break;

            case "status":
                if (!requirePlayerName(sender, args)) {
                    return true;
                }
                showStatus(sender, args[1]);
                break;

            case "unlock":
                if (!requirePlayerName(sender, args)) {
                    return true;
                }
                unlockPlayer(sender, args[1]);
                break;

            case "logout":
                if (!requirePlayerName(sender, args)) {
                    return true;
                }
                logoutPlayer(sender, args[1]);
                break;

            case "info":
                if (!requirePlayerName(sender, args)) {
                    return true;
                }
                showInfo(sender, args[1]);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private boolean requirePlayerName(
            CommandSender sender,
            String[] args
    ) {
        if (args.length < 2 || args[1].isBlank()) {
            sender.sendMessage(color("&cNama pemain belum diberikan."));
            return false;
        }

        return true;
    }

    private void showStatus(
            CommandSender sender,
            String playerName
    ) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        boolean registered = accountManager.exists(
                target.getUniqueId()
        );

        boolean authenticated = authenticationManager.isAuthenticated(
                target.getUniqueId()
        );

        boolean locked = authenticationManager.isLocked(
                target.getUniqueId()
        );

        sender.sendMessage(color("&8&m----------&r &bVortexAuth Status &8&m----------"));
        sender.sendMessage(color("&7Player: &f" + playerName));
        sender.sendMessage(color("&7Registered: " + status(registered)));
        sender.sendMessage(color("&7Authenticated: " + status(authenticated)));
        sender.sendMessage(color("&7Locked: " + status(locked)));
        sender.sendMessage(color("&8&m---------------------------------------"));
    }

    private void unlockPlayer(
            CommandSender sender,
            String playerName
    ) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        authenticationManager.getLoginAttemptManager()
                .resetAttempts(target.getUniqueId());

        sender.sendMessage(color(
                "&aLogin lock untuk &f" + playerName + " &aberhasil dibuka."
        ));
    }

    private void logoutPlayer(
            CommandSender sender,
            String playerName
    ) {
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            sender.sendMessage(color("&cPemain tersebut sedang tidak online."));
            return;
        }

        authenticationManager.invalidateSession(
                target.getUniqueId()
        );

        target.sendMessage(color("&cSesi autentikasi kamu telah diakhiri oleh admin."));

        sender.sendMessage(color(
                "&aSesi &f" + playerName + " &aberhasil diakhiri."
        ));
    }

    private void showInfo(
            CommandSender sender,
            String playerName
    ) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        try {
            Account account = accountManager.getAccount(
                    target.getUniqueId()
            );

            if (account == null) {
                sender.sendMessage(color("&cAkun tidak ditemukan."));
                return;
            }

            sender.sendMessage(color("&8&m----------&r &bVortexAuth Info &8&m----------"));
            sender.sendMessage(color("&7Username: &f" + account.username()));
            sender.sendMessage(color("&7UUID: &f" + account.uuid()));
            sender.sendMessage(color("&7Platform: &f" + account.platform()));
            sender.sendMessage(color("&7Premium: " + status(account.isPremium())));
            sender.sendMessage(color("&7Bedrock: " + status(account.isBedrock())));
            sender.sendMessage(color("&8&m--------------------------------------"));

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to load account information: "
                            + exception.getMessage()
            );

            sender.sendMessage(color("&cGagal mengambil data akun."));
        }
    }

    private String status(boolean value) {
        return value ? "&aYes" : "&cNo";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m----------&r &bVortexAuth Admin &8&m----------"));
        sender.sendMessage(color("&b/authadmin reload &7- Reload konfigurasi"));
        sender.sendMessage(color("&b/authadmin status <player> &7- Status autentikasi"));
        sender.sendMessage(color("&b/authadmin unlock <player> &7- Buka kunci login"));
        sender.sendMessage(color("&b/authadmin logout <player> &7- Akhiri sesi"));
        sender.sendMessage(color("&b/authadmin info <player> &7- Informasi akun"));
        sender.sendMessage(color("&8&m---------------------------------------"));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
