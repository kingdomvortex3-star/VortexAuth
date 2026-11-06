
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class AuthTimeoutListener implements Listener {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public AuthTimeoutListener(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        )) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.login.enabled", true
        )) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.login.timeout-enabled", true
        )) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("vortexauth.bypass")) {
            return;
        }

        int timeoutSeconds = plugin.getConfig().getInt(
                "authentication.login.timeout-seconds", 60
        );

        if (timeoutSeconds <= 0) {
            return;
        }

        long delayTicks = timeoutSeconds * 20L;

        player.getScheduler().runDelayed(
                plugin,
                task -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (authenticationManager.isAuthenticated(
                            player.getUniqueId()
                    )) {
                        return;
                    }

                    String message = plugin.getConfig().getString(
                            "messages.login-timeout",
                            "&cWaktu login habis!"
                    );

                    player.kick(
                            LegacyComponentSerializer.legacyAmpersand()
                                    .deserialize(message)
                    );
                },
                () -> {
                    // Player sudah tidak tersedia pada scheduler.
                },
                delayTicks
        );
    }
}
