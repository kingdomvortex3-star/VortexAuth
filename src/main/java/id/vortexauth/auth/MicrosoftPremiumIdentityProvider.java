
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.MinecraftProfileManager.MinecraftProfile;
import id.vortexauth.auth.MicrosoftOAuthManager.DeviceCode;
import id.vortexauth.auth.MicrosoftOAuthManager.MicrosoftToken;
import id.vortexauth.auth.XboxLiveManager.XboxToken;
import id.vortexauth.auth.XstsManager.XstsToken;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public final class MicrosoftPremiumIdentityProvider
        implements PremiumVerificationManager.PremiumIdentityProvider {

    private final VortexAuth plugin;
    private final MicrosoftOAuthManager microsoftOAuthManager;
    private final XboxLiveManager xboxLiveManager;
    private final XstsManager xstsManager;
    private final MinecraftProfileManager profileManager;

    public MicrosoftPremiumIdentityProvider(
            VortexAuth plugin,
            MicrosoftOAuthManager microsoftOAuthManager,
            XboxLiveManager xboxLiveManager,
            XstsManager xstsManager,
            MinecraftProfileManager profileManager
    ) {
        this.plugin = plugin;
        this.microsoftOAuthManager = microsoftOAuthManager;
        this.xboxLiveManager = xboxLiveManager;
        this.xstsManager = xstsManager;
        this.profileManager = profileManager;
    }

    @Override
    public CompletableFuture<PremiumVerificationManager.VerifiedIdentity>
    verify(Player player) {

        if (player == null || !player.isOnline()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Player is unavailable.")
            );
        }

        String expectedUsername = player.getName();

        return microsoftOAuthManager.requestDeviceCode()
                .thenCompose(deviceCode -> {
                    sendDeviceCode(player, deviceCode);

                    return microsoftOAuthManager
                            .pollForToken(deviceCode);
                })
                .thenCompose(microsoftToken ->
                        xboxLiveManager.authenticate(microsoftToken)
                )
                .thenCompose(xboxToken ->
                        xstsManager.authorize(xboxToken)
                )
                .thenCompose(xstsToken ->
                        profileManager.getProfile(xstsToken)
                )
                .thenApply(profile ->
                        createIdentity(profile, expectedUsername)
                );
    }

    private PremiumVerificationManager.VerifiedIdentity createIdentity(
            MinecraftProfile profile,
            String expectedUsername
    ) {
        if (profile == null
                || profile.username() == null
                || profile.uuid() == null) {
            throw new IllegalStateException(
                    "Minecraft profile is invalid."
            );
        }

        if (!profile.username().equalsIgnoreCase(expectedUsername)) {
            throw new IllegalStateException(
                    "Verified Minecraft username does not match server username."
            );
        }

        return new PremiumVerificationManager.VerifiedIdentity(
                true,
                profile.username(),
                profile.uuid()
        );
    }

    private void sendDeviceCode(
            Player player,
            DeviceCode deviceCode
    ) {
        if (!player.isOnline()) {
            return;
        }

        player.getScheduler().run(
                plugin,
                task -> {
                    player.sendMessage(" ");
                    player.sendMessage("§8§m--------------------------------");
                    player.sendMessage("§b§lVortexAuth Premium Verification");
                    player.sendMessage("§7Buka halaman Microsoft berikut:");
                    player.sendMessage("§b" + deviceCode.verificationUri());
                    player.sendMessage("§7Masukkan kode berikut:");
                    player.sendMessage("§a§l" + deviceCode.userCode());
                    player.sendMessage("§7Selesaikan verifikasi pada Microsoft.");
                    player.sendMessage("§8§m--------------------------------");
                    player.sendMessage(" ");
                },
                null
        );
    }
}
