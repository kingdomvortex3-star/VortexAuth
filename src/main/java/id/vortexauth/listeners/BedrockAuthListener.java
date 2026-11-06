
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager;
import id.vortexauth.auth.AccountManager.Account;
import id.vortexauth.auth.AuthenticationManager;
import id.vortexauth.bedrock.FloodgateManager;
import id.vortexauth.security.IpAddressManager;
import id.vortexauth.utils.SecurityUtils;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.Optional;

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onBedrockJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getBoolean(
                "bedrock.enabled",
                true
        )) {
            return;
        }

        if (!floodgateManager.isBedrock(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "bedrock.auto-login.enabled",
                true
        )) {
            player.sendMessage(color(
                    "&eBedrock terdeteksi. Silakan ikuti proses autentikasi."
            ));
            return;
        }

        String xuid = floodgateManager.getXuid(player);

        if (!SecurityUtils.isValidXuid(xuid)) {
            player.kickPlayer(color("&cXUID Floodgate tidak valid."));
            return;
        }

        try {
            Optional<Account> optionalAccount =
                    accountManager.getAccountByXuid(xuid);

            if (optionalAccount.isEmpty()) {
                player.sendMessage(color(
                        "&cAkun Bedrock kamu belum terhubung."
                ));
                player.sendMessage(color(
                        "&7Hubungi administrator untuk proses linking."
                ));
                return;
            }

            Account account = optionalAccount.get();

            if (!account.isBedrock()) {
                player.kickPlayer(color(
                        "&cAkun ini bukan akun Bedrock."
                ));
                return;
            }

            if (!account.uuid().equals(player.getUniqueId())) {
                player.kickPlayer(color(
                        "&cIdentitas akun Bedrock tidak cocok."
                ));
                return;
            }

            String ipHash = ipAddressManager.getPlayerIpHash(player);

            if (authenticationManager.isAuthenticated(
                    player.getUniqueId(),
                    ipHash
            )) {
                return;
            }

            authenticationManager.createBedrockSession(
                    player.getUniqueId(),
                    ipHash
            );

            if (authenticationManager.isAuthenticated(
                    player.getUniqueId(),
                    ipHash
            )) {
                player.sendMessage(color(
                        "&aAutentikasi Bedrock berhasil!"
                ));
            } else {
                player.sendMessage(color(
                        "&cAutentikasi Bedrock gagal. Silakan hubungi administrator."
                ));
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to authenticate Bedrock player: "
                            + exception.getMessage()
            );

            player.kickPlayer(color(
                    "&cDatabase sedang bermasalah. Silakan coba lagi."
            ));
        }
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
