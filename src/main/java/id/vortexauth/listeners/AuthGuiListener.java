
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AuthGuiListener implements Listener {

    private static final String MENU_TITLE =
            ChatColor.translateAlternateColorCodes(
                    '&',
                    "&8VortexAuth &7- &bAuthentication"
            );

    private final VortexAuth plugin;

    public AuthGuiListener(VortexAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();

        if (!isAuthMenu(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null
                || event.getClickedInventory() != topInventory) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        if (clickedItem.getType() == Material.BARRIER
                && event.getRawSlot() == 22) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isAuthMenu(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isAuthMenu(event.getView().getTitle())) {
            return;
        }

        if (plugin.getConfig().getBoolean(
                "settings.debug", false
        )) {
            plugin.getLogger().info(
                    event.getPlayer().getName()
                            + " closed the authentication menu."
            );
        }
    }

    private boolean isAuthMenu(String title) {
        return MENU_TITLE.equals(title);
    }
}
