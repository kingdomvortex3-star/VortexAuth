
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.security.AntiVpnManager;
import id.vortexauth.security.AntiVpnManager.VpnResult;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetSocketAddress;

public final class AntiVpnListener implements Listener {

    private final VortexAuth plugin;
    private final AntiVpnManager antiVpnManager;

    public AntiVpnListener(
            VortexAuth plugin,
            AntiVpnManager antiVpnManager
    ) {
        this.plugin = plugin;
        this.antiVpnManager = antiVpnManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean(
                "anti-vpn.enabled", true
        )) {
            return;
        }

        Player player = event.getPlayer();

        InetSocketAddress socketAddress = player.getAddress();

        if (socketAddress == null
                || socketAddress.getAddress() == null) {
            return;
        }

        String ipAddress = socketAddress.getAddress()
                .getHostAddress();

        antiVpnManager.check(ipAddress).thenAccept(result -> {
            if (!player.isOnline()) {
                return;
            }

            player.getScheduler().run(
                    plugin,
                    task -> handleResult(player, result),
                    null
            );
        }).exceptionally(error -> {
            plugin.getLogger().warning(
                    "Anti-VPN check failed: " + error.getMessage()
            );
            return null;
        });
    }

    private void handleResult(Player player, VpnResult result) {
        if (!player.isOnline() || !result.known()) {
            return;
        }

        if (!result.blocked()) {
            return;
        }

        String action = plugin.getConfig().getString(
                "anti-vpn.action", "BLOCK"
        );

        if ("BLOCK".equalsIgnoreCase(action)) {
            String message = plugin.getConfig().getString(
                    "messages.vpn-blocked",
                    "&cKoneksi VPN/Proxy tidak diperbolehkan!"
            );

            player.kick(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(message)
            );
        } else if ("NOTIFY".equalsIgnoreCase(action)) {
            plugin.getLogger().warning(
                    "VPN/Proxy detected for player: "
                            + player.getName()
            );
        }
    }
}
