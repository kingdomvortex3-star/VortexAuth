
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class AuthCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final AuthenticationManager authenticationManager;

    public AuthCommand(
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
            sender.sendMessage("Command ini hanya bisa digunakan oleh pemain.");
            return true;
        }

        Optional<AccountManager.Account> account =
                accountManager.getAccount(player.getUniqueId());

        boolean authenticated =
                authenticationManager.isAuthenticated(player.getUniqueId());

        String prefix = ChatColor.translateAlternateColorCodes(
                '&',
                plugin.getConfig().getString(
                        "messages.prefix",
                        "&8[&bVortexAuth&8] "
                )
        );

        player.sendMessage(prefix + ChatColor.GRAY + "Status autentikasi:");

        if (account.isEmpty()) {
            player.sendMessage(
                    ChatColor.RED + "Akun belum terdaftar."
            );
            player.sendMessage(
                    ChatColor.YELLOW + "Gunakan /register <password> <konfirmasi>"
            );
            return true;
        }

        if (authenticated) {
            player.sendMessage(
                    ChatColor.GREEN + "Status: Terautentikasi"
            );
        } else {
            player.sendMessage(
                    ChatColor.RED + "Status: Belum login"
            );
            player.sendMessage(
                    ChatColor.YELLOW + "Gunakan /login <password>"
            );
        }

        return true;
    }
}
