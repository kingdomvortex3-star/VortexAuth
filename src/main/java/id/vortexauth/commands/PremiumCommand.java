
package id.vortexauth.commands;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.auth.PremiumVerificationManager;
import id.vortexauth.auth.PremiumVerificationManager.VerificationResult;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PremiumCommand implements CommandExecutor {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;
    private final PremiumVerificationManager verificationManager;

    private final Set<UUID> pending =
            ConcurrentHashMap.newKeySet();

    public PremiumCommand(
            VortexAuth plugin,
            AuthenticationManager authenticationManager,
            PremiumVerificationManager verificationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
        this.verificationManager = verificationManager;
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

        if (!plugin.getConfig().getBoolean(
                "java.premium.enabled", true
        )) {
            player.sendMessage(color(
                    "&cPremium Authentication sedang dinonaktifkan."
            ));
            return true;
        }

        if (!authenticationManager.isAuthenticated(
                player.getUniqueId()
        )) {
            player.sendMessage(color(
                    plugin.getConfig().getString(
                            "messages.login-required",
                            "&cSilakan login terlebih dahulu!"
                    )
            ));
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (!pending.add(uuid)) {
            player.sendMessage(color(
                    "&eVerifikasi premium kamu sedang berlangsung."
            ));
            return true;
        }

        player.sendMessage(color("&bMemulai verifikasi Microsoft..."));
        player.sendMessage(color(
                "&7Ikuti instruksi kode verifikasi yang akan dikirim."
        ));

        verificationManager.verify(player)
                .whenComplete((result, error) -> {
                    pending.remove(uuid);

                    if (!player.isOnline()) {
                        return;
                    }

                    player.getScheduler().run(
                            plugin,
                            task -> handleResult(player, result, error),
                            null
                    );
                });

        return true;
    }

    private void handleResult(
            Player player,
            VerificationResult result,
            Throwable error
    ) {
        if (!player.isOnline()) {
            return;
        }

        if (error != null || result == null) {
            player.sendMessage(color(
                    "&cVerifikasi gagal karena terjadi kesalahan."
            ));
            return;
        }

        if (!result.configured()) {
            player.sendMessage(color(
                    "&cMicrosoft OAuth belum dikonfigurasi oleh administrator."
            ));
            return;
        }

        if (!result.success()) {
            player.sendMessage(color(
                    "&cVerifikasi premium gagal: &7" + result.message()
            ));
            return;
        }

        player.sendMessage(color(
                plugin.getConfig().getString(
                        "messages.premium-enabled",
                        "&aPremium Authentication berhasil diaktifkan!"
                )
        ));

        player.sendMessage(color(
                "&7Minecraft Java: &f" + result.username()
        ));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                message == null ? "" : message
        );
    }
}
