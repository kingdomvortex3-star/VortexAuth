
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.auth.AccountManager.Account;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.Arrays;

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
            sender.sendMessage(color("&cPerintah ini hanya untuk pemain!"));
            return true;
        }

        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
                null,
                27,
                color("&8VortexAuth &7- &bAuthentication")
        );

        boolean authenticated =
                authenticationManager.isAuthenticated(player.getUniqueId());

        Account account;

        try {
            account = accountManager.getAccount(player.getUniqueId());
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to load account for " + player.getUniqueId()
                            + ": " + exception.getMessage()
            );

            player.sendMessage(color("&cGagal mengambil data akun."));
            return;
        }

        if (account == null) {
            inventory.setItem(11, createItem(
                    Material.BOOK,
                    "&b&lREGISTER",
                    "&7Akun belum terdaftar.",
                    "&eGunakan: /register <password> <konfirmasi>"
            ));
        } else {
            inventory.setItem(11, createItem(
                    Material.BOOK,
                    "&b&lACCOUNT",
                    "&7Username: &f" + player.getName(),
                    "&7Status: " + (authenticated ? "&aLogin" : "&cBelum login")
            ));
        }

        inventory.setItem(13, createItem(
                authenticated ? Material.LIME_DYE : Material.RED_DYE,
                authenticated ? "&a&lAUTHENTICATED" : "&c&lNOT AUTHENTICATED",
                authenticated
                        ? "&7Kamu sudah berhasil login."
                        : "&7Kamu belum melakukan login.",
                authenticated
                        ? "&7Gunakan /logout untuk keluar."
                        : "&7Gunakan /login <password>."
        ));

        inventory.setItem(15, createItem(
                Material.TRIPWIRE_HOOK,
                "&6&lPREMIUM AUTH",
                "&7Autentikasi akun Minecraft resmi.",
                "&eGunakan: /premium"
        ));

        inventory.setItem(22, createItem(
                Material.BARRIER,
                "&c&lCLOSE",
                "&7Tutup menu autentikasi."
        ));

        player.openInventory(inventory);
    }

    private ItemStack createItem(
            Material material,
            String name,
            String... lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(Arrays.stream(lore)
                    .map(this::color)
                    .toList());
            item.setItemMeta(meta);
        }

        return item;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
