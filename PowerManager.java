package dev.smp.eclipsepowers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class PowerManager {

    private final EclipsePowers plugin;
    private final NamespacedKey powerKey;
    private final NamespacedKey killsKey;
    private final Abilities abilities;

    private final Map<UUID, long[]> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastPress = new HashMap<>();
    private final Map<UUID, Long> silencedUntil = new HashMap<>();
    private final Map<UUID, Long> shieldedUntil = new HashMap<>();

    public PowerManager(EclipsePowers plugin) {
        this.plugin = plugin;
        this.powerKey = new NamespacedKey(plugin, "power");
        this.killsKey = new NamespacedKey(plugin, "kills");
        this.abilities = new Abilities(plugin, this);
    }

    // ---------- config ----------

    public int killsToUnlock() {
        return Math.max(1, plugin.getConfig().getInt("kills-to-unlock", 5));
    }

    public boolean countMobKills() {
        return plugin.getConfig().getBoolean("count-mob-kills", false);
    }

    /** Seconds between a player joining and their power roll starting. */
    public int rollDelaySeconds() {
        return Math.max(0, plugin.getConfig().getInt("roll-delay-seconds", 5));
    }

    public boolean assignOnJoin() {
        return plugin.getConfig().getBoolean("assign-on-join", true);
    }

    // ---------- stored data (lives on the player, survives restarts) ----------

    public Power getPower(Player p) {
        String name = p.getPersistentDataContainer().get(powerKey, PersistentDataType.STRING);
        return Power.fromName(name);
    }

    /** Sets the power and resets kills, so the player starts again at ability 1. */
    public void setPower(Player p, Power power) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(powerKey, PersistentDataType.STRING, power.name());
        pdc.set(killsKey, PersistentDataType.INTEGER, 0);
        cooldowns.remove(p.getUniqueId());
    }

    public int getKills(Player p) {
        Integer kills = p.getPersistentDataContainer().get(killsKey, PersistentDataType.INTEGER);
        return kills == null ? 0 : kills;
    }

    public void setKills(Player p, int kills) {
        p.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, Math.max(0, kills));
    }

    public boolean hasSecondAbility(Player p) {
        return getKills(p) >= killsToUnlock();
    }

    /** A random power for this player, never the one they already have. */
    public Power pickRandom(Player p) {
        Power current = getPower(p);
        Power[] all = Power.values();
        Power chosen;
        do {
            chosen = all[ThreadLocalRandom.current().nextInt(all.length)];
        } while (chosen == current);
        return chosen;
    }

    public Abilities abilities() {
        return abilities;
    }

    /** The shield just absorbed a hit: show the ripple. */
    public void shieldBlocked(Player p) {
        abilities.shieldRipple(p);
    }

    // ---------- messages ----------

    public void awaken(Player p, Power power) {
        p.showTitle(Title.title(
                Component.text(power.displayName(), power.color(), TextDecoration.BOLD),
                Component.text("Your power awakens", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(600))));
        p.sendMessage(Component.text("You now wield the power of ", NamedTextColor.GRAY)
                .append(Component.text(power.displayName(), power.color(), TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("[Offhand key] ", NamedTextColor.YELLOW)
                .append(Component.text(power.abilityName(1), power.color())));
        p.sendMessage(Component.text("[Shift + Offhand key] ", NamedTextColor.YELLOW)
                .append(Component.text(power.abilityName(2), NamedTextColor.DARK_GRAY))
                .append(Component.text(" (unlocks at " + killsToUnlock() + " kills)", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        abilities.awakenEffect(p, power);
    }

    // ---------- kills / level up ----------

    public void addKill(Player killer, Location where) {
        Power power = getPower(killer);
        if (power == null) return;
        abilities.killEffect(killer, where, power);

        int before = getKills(killer);
        int after = before + 1;
        setKills(killer, after);

        int need = killsToUnlock();
        if (before < need && after >= need) {
            killer.showTitle(Title.title(
                    Component.text("LEVEL UP", power.color(), TextDecoration.BOLD),
                    Component.text(power.abilityName(2) + " unlocked", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            killer.sendMessage(Component.text("Level up! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(power.abilityName(2), power.color()))
                    .append(Component.text(" is unlocked. Use Shift + Offhand key.", NamedTextColor.GRAY)));
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            abilities.levelUpEffect(killer, power);
        } else if (after < need) {
            killer.sendActionBar(Component.text("Kills: " + after + "/" + need, NamedTextColor.GRAY));
        }
    }

    // ---------- using abilities ----------

    /** slot 1 = offhand key, slot 2 = shift + offhand key */
    public void use(Player p, int slot) {
        Power power = getPower(p);
        if (power == null) return;

        long now = System.currentTimeMillis();
        Long lastUse = lastPress.get(p.getUniqueId());
        if (lastUse != null && now - lastUse < 150) return; // ignore key-repeat and double presses
        lastPress.put(p.getUniqueId(), now);

        // Ability 2 is locked until the player has enough kills. Private message, only they see it.
        if (slot == 2 && !hasSecondAbility(p)) {
            int left = killsToUnlock() - getKills(p);
            p.sendMessage(Component.text("[Locked] ", NamedTextColor.RED)
                    .append(Component.text(power.abilityName(2), power.color()))
                    .append(Component.text(" unlocks at " + killsToUnlock() + " kills. You need "
                            + left + " more kill" + (left == 1 ? "" : "s") + ".", NamedTextColor.GRAY)));
            return;
        }

        if (isSilenced(p)) {
            p.sendActionBar(Component.text("You are silenced by Null magic!", NamedTextColor.RED));
            return;
        }

        long[] cd = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new long[2]);
        long remaining = cd[slot - 1] - now;
        if (remaining > 0) {
            long seconds = (remaining + 999) / 1000;
            p.sendActionBar(Component.text(power.abilityName(slot) + " on cooldown: " + seconds + "s",
                    NamedTextColor.RED));
            return;
        }

        try {
            if (abilities.cast(p, power, slot)) {
                cd[slot - 1] = now + power.cooldownSeconds(slot) * 1000L;
                p.sendActionBar(Component.text(power.abilityName(slot), power.color(), TextDecoration.BOLD));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE,
                    "Ability " + power.abilityName(slot) + " failed for " + p.getName(), t);
            p.sendMessage(Component.text("That ability hit an error. An admin can see the details in the console.",
                    NamedTextColor.RED));
        }
    }

    /** Milliseconds left on an ability's cooldown (0 when it is ready). */
    public long cooldownRemainingMs(Player p, int slot) {
        long[] cd = cooldowns.get(p.getUniqueId());
        if (cd == null) return 0;
        return Math.max(0, cd[slot - 1] - System.currentTimeMillis());
    }

    // ---------- silence / shield (used by Null) ----------

    public void silence(Player p, long millis) {
        silencedUntil.put(p.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public boolean isSilenced(Player p) {
        Long until = silencedUntil.get(p.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            silencedUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    public void shield(Player p, long millis) {
        shieldedUntil.put(p.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public boolean isShielded(Player p) {
        Long until = shieldedUntil.get(p.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            shieldedUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }
}
