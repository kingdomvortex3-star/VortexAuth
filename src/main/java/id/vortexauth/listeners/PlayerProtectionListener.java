
package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class PlayerProtectionListener implements Listener {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public PlayerProtectionListener(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    private boolean isProtected(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        )) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "security.enabled", true

package id.vortexauth.listeners;

import id.vortexauth.VortexAuth;
import id.vortexauth.auth.AuthenticationManager;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class PlayerProtectionListener implements Listener {

    private final VortexAuth plugin;
    private final AuthenticationManager authenticationManager;

    public PlayerProtectionListener(
            VortexAuth plugin,
            AuthenticationManager authenticationManager
    ) {
        this.plugin = plugin;
        this.authenticationManager = authenticationManager;
    }

    private boolean isProtected(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "authentication.enabled", true
        )) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "security.enabled", true
        )) {
            return false;
        }

        if (player.hasPermission("vortexauth.bypass")) {
            return false;
        }

        return !authenticationManager.isAuthenticated(
                player.getUniqueId()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-movement", true
        )) {
            return;
        }

        if (!isProtected(event.getPlayer())) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        boolean moved =
                event.getFrom().getBlockX()
                        != event.getTo().getBlockX()
                || event.getFrom().getBlockY()
                        != event.getTo().getBlockY()
                || event.getFrom().getBlockZ()
                        != event.getTo().getBlockZ();

        if (moved) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-interaction", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-interaction", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-interaction", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-interaction", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-interaction", true
        )) {
            return;
        }

        Player attacker = getAttackingPlayer(event.getDamager());

        if (attacker != null && isProtected(attacker)) {
            event.setCancelled(true);
        }
    }

    private Player getAttackingPlayer(
            org.bukkit.entity.Entity damager
    ) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-inventory", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-inventory", true
        )) {
            return;
        }

        if (event.getEntity() instanceof Player player
                && isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-inventory", true
        )) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!isProtected(player)) {
            return;
        }

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                .equals(player.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-inventory", true
        )) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!isProtected(player)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();

        if (event.getRawSlots().stream()
                .anyMatch(slot -> slot >= topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-interaction", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!plugin.getConfig().getBoolean(
                "security.prevent-inventory", true
        )) {
            return;
        }

        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
