
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AccountManager.Account;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.bedrock.FloodgateManager;
import id.vortexauth.security.IpAddressManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;

public final class BedrockAuthListener implements Listener {

    private final VortexAuth plugin;
    private final FloodgateManager floodgateManager;
    private final AccountManager accountManager;
    private final AuthenticationManager authenticationManager;
    private final IpAddressManager ipAddressManager;

    public BedrockAuthListener(
            VortexAuth plugin,
            FloodgateManager floodgateManager,
            AccountManager accountManager,
            AuthenticationManager authenticationManager,
            IpAddressManager ipAddressManager
    ) {
        this.plugin = plugin;
        this.floodgateManager = floodgateManager;
        this.accountManager = accountManager;
        this.authenticationManager = authenticationManager;
        this.ipAddressManager = ipAddressManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedrockJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getBoolean("bedrock.enabled", true)) {
            return;
        }

        if (!floodgateManager.isBedrock(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "bedrock.auto-login.enabled", true
        )) {
            player.sendMessage(color("&eBedrock terdeteksi. Silakan ikuti proses autentikasi."));
            return;
        }

        String xuid = floodgateManager.getXuid(player);

        if (xuid == null) {
            player.kick(color("&cXUID Floodgate tidak valid."));
            return;
        }

        try {
            Account account = accountManager.getAccountByXuid(xuid);

            if (account == null) {
                player.sendMessage(color("&cAkun Bedrock kamu belum terhubung."));
                player.sendMessage(color("&7Hubungi administrator untuk proses linking."));
                return;
            }

            if (!account.uuid().equals(player.getUniqueId())) {
                player.kick(color("&cIdentitas akun Bedrock tidak cocok."));
                return;
            }

            String ipHash = ipAddressManager.getPlayerIpHash(player);

            if (authenticationManager.getSessionManager()
                    .isAuthenticated(player.getUniqueId(), ipHash)) {
                return;
            }

            authenticationManager.createBedrockSession(
                    player.getUniqueId(),
                    ipHash
            );

            player.sendMessage(color("&aAutentikasi Bedrock berhasil!"));

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to authenticate Bedrock player: "
                            + exception.getMessage()
            );

            player.kick(color("&cDatabase sedang bermasalah. Silakan coba lagi."));
        }
    }

    private String color(String message) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }
}
