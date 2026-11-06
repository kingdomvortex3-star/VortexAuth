
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AccountManager.Account;

import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PremiumVerificationManager {

    private final VortexAuth plugin;
    private final AccountManager accountManager;
    private final PremiumIdentityProvider identityProvider;

    public PremiumVerificationManager(
            VortexAuth plugin,
            AccountManager accountManager,
            PremiumIdentityProvider identityProvider
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.identityProvider = identityProvider;
    }

    public CompletableFuture<VerificationResult> verify(Player player) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(
                    VerificationResult.failed("Player unavailable")
            );
        }

        if (!plugin.getConfig().getBoolean(
                "java.premium.enabled", true
        )) {
            return CompletableFuture.completedFuture(
                    VerificationResult.failed("Premium authentication disabled")
            );
        }

        if (identityProvider == null) {
            return CompletableFuture.completedFuture(
                    VerificationResult.notConfigured()
            );
        }

        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        return identityProvider.verify(player)
                .thenApply(identity -> {
                    if (identity == null || !identity.verified()) {
                        return VerificationResult.failed(
                                "Official account verification failed"
                        );
                    }

                    if (identity.username() == null
                            || !identity.username().equalsIgnoreCase(
                                    playerName
                            )) {
                        return VerificationResult.failed(
                                "Official Minecraft username does not match"
                        );
                    }

                    try {
                        Account account = accountManager.getAccount(
                                playerUuid
                        );

                        if (account == null) {
                            return VerificationResult.failed(
                                    "VortexAuth account not found"
                            );
                        }

                        accountManager.setPremiumStatus(
                                playerUuid,
                                true,
                                true
                        );

                        return VerificationResult.success(
                                identity.username(),
                                identity.minecraftUuid()
                        );

                    } catch (SQLException exception) {
                        plugin.getLogger().severe(
                                "Premium verification database error: "
                                        + exception.getMessage()
                        );

                        return VerificationResult.failed(
                                "Database error"
                        );
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning(
                            "Premium verification failed: "
                                    + error.getClass().getSimpleName()
                    );

                    return VerificationResult.failed(
                            "Verification provider unavailable"
                    );
                });
    }

    public boolean isPremiumEnabled(UUID playerUuid) {
        try {
            Account account = accountManager.getAccount(playerUuid);

            return account != null
                    && account.premiumEnabled()
                    && account.premiumVerified();

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Failed to check premium status: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    public interface PremiumIdentityProvider {

        CompletableFuture<VerifiedIdentity> verify(Player player);
    }

    public record VerifiedIdentity(
            boolean verified,
            String username,
            UUID minecraftUuid
    ) {
    }

    public record VerificationResult(
            boolean success,
            boolean configured,
            String message,
            String username,
            UUID minecraftUuid
    ) {

        public static VerificationResult success(
                String username,
                UUID minecraftUuid
        ) {
            return new VerificationResult(
                    true,
                    true,
                    "Verification successful",
                    username,
                    minecraftUuid
            );
        }

        public static VerificationResult failed(String message) {
            return new VerificationResult(
                    false,
                    true,
                    message,
                    null,
                    null
            );
        }

        public static VerificationResult notConfigured() {
            return new VerificationResult(
                    false,
                    false,
                    "Premium verification provider is not configured",
                    null,
                    null
            );
        }
    }
}
