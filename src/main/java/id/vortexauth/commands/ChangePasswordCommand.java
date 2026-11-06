
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public final class ChangePasswordCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final AuthenticationManager authenticationManager;

    public ChangePasswordCommand(
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(
                    plugin.getConfig().getString(
                            "messages.player-only",
                            "&cPerintah ini hanya untuk pemain!"
                    )
            ));
            return true;
        }

        if (player.hasPermission("vortexauth.bypass")) {
            player.sendMessage(color("&cKamu tidak perlu mengganti password melalui VortexAuth."));
            return true;
        }

        if (!authenticationManager.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(color(
                    plugin.getConfig().getString(
                            "messages.login-required",
                            "&cSilakan login terlebih dahulu!"
                    )
            ));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(color("&cGunakan: /changepassword <password-lama> <password-baru>"));
            return true;
        }

        String oldPassword = args[0];
        String newPassword = args[1];

        int minLength = plugin.getConfig().getInt(
                "authentication.register.min-password-length", 6
        );

        int maxLength = plugin.getConfig().getInt(
                "authentication.register.max-password-length", 32
        );

        if (newPassword.length() < minLength) {
            player.sendMessage(color(
                    plugin.getConfig().getString(
                            "messages.password-too-short",
                            "&cPassword terlalu pendek!"
                    )
            ));
            return true;
        }

        if (newPassword.length() > maxLength) {
            player.sendMessage(color("&cPassword terlalu panjang! Maksimal " + maxLength + " karakter."));
            return true;
        }

        try {
            boolean changed = accountManager.changePassword(
                    player.getUniqueId(),
                    oldPassword,
                    newPassword
            );

            if (changed) {
                player.sendMessage(color(
                        plugin.getConfig().getString(
                                "messages.password-changed",
                                "&aPassword berhasil diubah!"
                        )
                ));
            } else {
                player.sendMessage(color(
                        plugin.getConfig().getString(
                                "messages.invalid-password",
                                "&cPassword lama yang kamu masukkan salah!"
                        )
                ));
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to change password for " + player.getUniqueId()
                            + ": " + exception.getMessage()
            );

            player.sendMessage(color("&cTerjadi kesalahan database. Silakan hubungi administrator."));
        }

        return true;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
