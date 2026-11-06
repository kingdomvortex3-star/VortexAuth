
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PremiumCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final AuthenticationManager authenticationManager;

    public PremiumCommand(
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
            sender.sendMessage(color("&cPerintah ini hanya untuk pemain!"));
            return true;
        }

        if (!plugin.getConfig().getBoolean("java.premium.enabled", true)) {
            player.sendMessage(color("&cFitur premium authentication sedang dinonaktifkan."));
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

        if (accountManager.getAccount(player.getUniqueId()) == null) {
            player.sendMessage(color("&cAkun VortexAuth kamu tidak ditemukan."));
            return true;
        }

        player.sendMessage(color("&b&lVortexAuth"));
        player.sendMessage(color("&7Untuk mengaktifkan Premium Authentication,"));
        player.sendMessage(color("&7kamu harus menyelesaikan verifikasi kepemilikan"));
        player.sendMessage(color("&7akun Minecraft Java resmi terlebih dahulu."));
        player.sendMessage(color(""));
        player.sendMessage(color("&eSistem verifikasi resmi belum tersedia."));
        player.sendMessage(color("&cStatus premium belum diubah."));

        return true;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
