package dev.smp.eclipsepowers;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;

public final class PowerListener implements Listener {

    private final EclipsePowers plugin;
    private final PowerManager manager;
    private final Menus menus;
    private final Roller roller;

    public PowerListener(EclipsePowers plugin, PowerManager manager, Menus menus, Roller roller) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = menus;
        this.roller = roller;
    }

    /**
     * Offhand key (F by default) = ability 1.
     * Shift + offhand key = ability 2.
     * The item swap is cancelled for players that have a power.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (manager.getPower(player) == null) return;
        if (!player.hasPermission("eclipsepowers.use")) return;

        event.setCancelled(true);
        manager.use(player, player.isSneaking() ? 2 : 1);
    }

    /** Counts kills toward the level up. */
    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) return;
        if (!(dead instanceof Player) && !manager.countMobKills()) return;

        manager.addKill(killer, dead.getLocation());
    }

    /** Null Shield: nullifies all damage while it is active. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && manager.isShielded(player)) {
            event.setCancelled(true);
            manager.shieldBlocked(player);
        }
    }

    /** Lets Dmg see whether a normal hit went through or was cancelled. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageEvent event) {
        Dmg.observe(event);
    }

    /** Players without a power roll one a few seconds after joining (roll-delay-seconds). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.getPower(player) == null && manager.assignOnJoin()) {
            roller.queueJoinRoll(player, manager.rollDelaySeconds());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        roller.finishNow(event.getPlayer());
    }

    // ---------- menus: nothing can be taken out, buttons are routed ----------

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menus.Holder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return;

        if (holder.type() == Menus.Type.INFO && event.getSlot() == Menus.CATALOG_BUTTON) {
            Fx.later(plugin, 1, () -> menus.openCatalog(player));
        } else if (holder.type() == Menus.Type.CATALOG && event.getSlot() == Menus.BACK_BUTTON) {
            Fx.later(plugin, 1, () -> menus.openInfo(player));
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menus.Holder) {
            event.setCancelled(true);
        }
    }
}
