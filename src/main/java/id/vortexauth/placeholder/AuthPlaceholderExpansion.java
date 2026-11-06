
package id.vortexauth.placeholder;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;

public final class AuthPlaceholderExpansion extends PlaceholderExpansion {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public AuthPlaceholderExpansion(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public String getIdentifier() {
        return "vortexauth";
    }

    @Override
    public String getAuthor() {
        return "VortexTeam";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(
            OfflinePlayer player,
            String params
    ) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        switch (params.toLowerCase()) {
            case "authenticated":
                return String.valueOf(
                        authenticationManager.isAuthenticated(
                                player.getUniqueId()
                        )
                );

            case "status":
                return authenticationManager.isAuthenticated(
                        player.getUniqueId()
                ) ? "LOGGED_IN" : "NOT_LOGGED_IN";

            case "locked":
                return String.valueOf(
                        authenticationManager.isLocked(
                                player.getUniqueId()
                        )
                );

            default:
                return null;
        }
    }
}
