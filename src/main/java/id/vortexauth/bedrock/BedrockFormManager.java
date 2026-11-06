
package id.vortexauth.bedrock;

import id.vortexauth.VortexAuth;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

public final class BedrockFormManager {

    private final VortexAuth plugin;
    private final FloodgateManager floodgateManager;
    private final AuthFormHandler handler;

    public BedrockFormManager(
            VortexAuth plugin,
            FloodgateManager floodgateManager,
            AuthFormHandler handler
    ) {
        this.plugin = plugin;
        this.floodgateManager = floodgateManager;
        this.handler = handler;
    }

    public void showLoginForm(Player player) {
        if (!canOpenForm(player)) {
            return;
        }

        CustomForm form = CustomForm.builder()
                .title("VortexAuth - Login")
                .label("§bWelcome to VortexAuth!")
                .label("§7Enter your account password below.")
                .input(
                        "Password",
                        "Enter your password",
                        ""
                )
                .validResultHandler(response -> {
                    String password = response.next();

                    if (password == null || password.isBlank()) {
                        player.sendMessage(color(
                                "&cPassword tidak boleh kosong!"
                        ));
                        return;
                    }

                    handler.onLogin(player, password);
                })
                .closedOrInvalidResultHandler(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(color(
                                "&eLogin form ditutup."
                        ));
                    }
                })
                .build();

        sendForm(player, form);
    }

    public void showRegisterForm(Player player) {
        if (!canOpenForm(player)) {
            return;
        }

        CustomForm form = CustomForm.builder()
                .title("VortexAuth - Register")
                .label("§bCreate your VortexAuth account.")
                .label("§7Choose a secure password.")
                .input(
                        "Password",
                        "Enter your password",
                        ""
                )
                .input(
                        "Confirm Password",
                        "Repeat your password",
                        ""
                )
                .validResultHandler(response -> {
                    String password = response.next();
                    String confirmation = response.next();

                    if (password == null || confirmation == null
                            || password.isBlank()
                            || confirmation.isBlank()) {
                        player.sendMessage(color(
                                "&cSemua kolom wajib diisi!"
                        ));
                        return;
                    }

                    handler.onRegister(
                            player,
                            password,
                            confirmation
                    );
                })
                .closedOrInvalidResultHandler(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(color(
                                "&eRegistration form ditutup."
                        ));
                    }
                })
                .build();

        sendForm(player, form);
    }

    private boolean canOpenForm(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "bedrock.enabled", true
        )) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "bedrock.ui.enabled", true
        )) {
            return false;
        }

        if (!floodgateManager.isAvailable()
                || !floodgateManager.isBedrock(player)) {
            return false;
        }

        return true;
    }

    private void sendForm(Player player, CustomForm form) {
        try {
            boolean sent = FloodgateApi.getInstance().sendForm(
                    player.getUniqueId(),
                    form
            );

            if (!sent) {
                player.sendMessage(color(
                        "&cGagal membuka formulir autentikasi."
                ));
            }

        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Failed to send Bedrock form: "
                            + exception.getClass().getSimpleName()
            );

            player.sendMessage(color(
                    "&cTerjadi kesalahan saat membuka formulir."
            ));
        }
    }

    private String color(String message) {
        return org.bukkit.ChatColor.translateAlternateColorCodes(
                '&',
                message
        );
    }

    public interface AuthFormHandler {

        void onLogin(Player player, String password);

        void onRegister(
                Player player,
                String password,
                String confirmation
        );
    }
}
