package dev.smp.eclipsepowers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Inventory menus: your power, the catalog of all powers, plus item helpers shared with the roll menu. */
public final class Menus {

    public enum Type {INFO, CATALOG}

    /** Marks an inventory as one of ours so clicks can be cancelled and routed. */
    public static final class Holder implements InventoryHolder {
        private final Type type;
        private Inventory inventory;

        public Holder(Type type) {
            this.type = type;
        }

        public Type type() {
            return type;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static final int CATALOG_BUTTON = 22; // in the info menu
    public static final int BACK_BUTTON = 31;    // in the catalog menu
    private static final int[] CATALOG_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    private final PowerManager manager;

    public Menus(PowerManager manager) {
        this.manager = manager;
    }

    // ------------------------------------------------------------------
    // your power
    // ------------------------------------------------------------------

    public void openInfo(Player p) {
        Power power = manager.getPower(p);
        if (power == null) {
            p.sendMessage(Component.text("You don't have a power yet.", NamedTextColor.GRAY));
            return;
        }
        int kills = manager.getKills(p);
        int need = manager.killsToUnlock();
        boolean unlocked = kills >= need;

        Holder holder = new Holder(Type.INFO);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Your power: ", NamedTextColor.GRAY)
                        .append(Component.text(power.displayName(), power.color(), TextDecoration.BOLD)));
        holder.bind(inv);

        ItemStack filler = item(power.pane(), Component.text(" "), null, false);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // the power itself
        List<Component> powerLore = new ArrayList<>();
        powerLore.add(text("Abilities:", NamedTextColor.GRAY));
        powerLore.add(text(" 1. " + power.abilityName(1), power.color()));
        powerLore.add(text(" 2. " + power.abilityName(2), unlocked ? power.color() : NamedTextColor.DARK_GRAY));
        inv.setItem(4, item(power.icon(), Component.text(power.displayName(), power.color(), TextDecoration.BOLD),
                powerLore, true));

        // ability 1
        inv.setItem(11, abilityItem(p, power, 1, true, kills, need));
        // kills / progress
        List<Component> progress = new ArrayList<>();
        progress.add(text(bar(kills, need), power.color()));
        progress.add(unlocked
                ? text("Both abilities unlocked.", NamedTextColor.GREEN)
                : text((need - kills) + " more kill" + (need - kills == 1 ? "" : "s") + " to unlock "
                + power.abilityName(2), NamedTextColor.GRAY));
        inv.setItem(13, item(Material.EXPERIENCE_BOTTLE,
                Component.text("Kills: " + Math.min(kills, need) + "/" + need, NamedTextColor.GOLD), progress, false));
        // ability 2
        inv.setItem(15, abilityItem(p, power, 2, unlocked, kills, need));

        inv.setItem(CATALOG_BUTTON, item(Material.BOOK, Component.text("All powers", NamedTextColor.YELLOW),
                List.of(text("See every power and what it can do.", NamedTextColor.GRAY)), false));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.4f);
    }

    private ItemStack abilityItem(Player p, Power power, int slot, boolean unlocked, int kills, int need) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(slot == 1 ? "[Offhand key]" : "[Shift + Offhand key]", NamedTextColor.YELLOW));
        if (unlocked) {
            for (String line : wrap(power.description(slot), 34)) lore.add(text(line, NamedTextColor.GRAY));
            lore.add(text("Cooldown: " + power.cooldownSeconds(slot) + "s", NamedTextColor.DARK_GRAY));
            long left = manager.cooldownRemainingMs(p, slot);
            lore.add(left > 0 ? text("Recharging: " + ((left + 999) / 1000) + "s left", NamedTextColor.RED)
                    : text("Ready", NamedTextColor.GREEN));
            return item(slot == 1 ? Material.BLAZE_POWDER : Material.NETHER_STAR,
                    Component.text(power.abilityName(slot), power.color(), TextDecoration.BOLD), lore, slot == 2);
        }
        lore.add(text("LOCKED", NamedTextColor.RED));
        lore.add(text("Unlocks at " + need + " kills (you have " + kills + ").", NamedTextColor.GRAY));
        return item(Material.GRAY_DYE,
                Component.text(power.abilityName(slot) + " (locked)", NamedTextColor.DARK_GRAY), lore, false);
    }

    // ------------------------------------------------------------------
    // all powers
    // ------------------------------------------------------------------

    public void openCatalog(Player p) {
        Power mine = manager.getPower(p);
        Holder holder = new Holder(Type.CATALOG);
        Inventory inv = Bukkit.createInventory(holder, 36, Component.text("All powers", NamedTextColor.GOLD));
        holder.bind(inv);

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), null, false);
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        Power[] all = Power.values();
        for (int i = 0; i < all.length && i < CATALOG_SLOTS.length; i++) {
            Power power = all[i];
            List<Component> lore = new ArrayList<>();
            if (power == mine) lore.add(text("This is your power", NamedTextColor.GREEN));
            for (int slot = 1; slot <= 2; slot++) {
                lore.add(Component.empty());
                lore.add(text((slot == 1 ? "[Offhand key] " : "[Shift + Offhand key] ") + power.abilityName(slot),
                        power.color()));
                for (String line : wrap(power.description(slot), 34)) lore.add(text(line, NamedTextColor.GRAY));
                lore.add(text("Cooldown: " + power.cooldownSeconds(slot) + "s"
                        + (slot == 2 ? " | unlocks at " + manager.killsToUnlock() + " kills" : ""),
                        NamedTextColor.DARK_GRAY));
            }
            inv.setItem(CATALOG_SLOTS[i], item(power.icon(),
                    Component.text(power.displayName(), power.color(), TextDecoration.BOLD), lore, power == mine));
        }

        if (mine != null) {
            inv.setItem(BACK_BUTTON, item(Material.ARROW, Component.text("Back to your power", NamedTextColor.YELLOW),
                    null, false));
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    // ------------------------------------------------------------------
    // item helpers
    // ------------------------------------------------------------------

    public static ItemStack item(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> fixed = new ArrayList<>();
            for (Component line : lore) fixed.add(noItalic(line));
            meta.lore(fixed);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private static Component text(String s, TextColor color) {
        return Component.text(s, color);
    }

    private static String bar(int value, int max) {
        int filled = (int) Math.round(10.0 * Math.min(value, max) / max);
        return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
    }

    /** Splits text into lines of at most `width` characters, on word boundaries. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
