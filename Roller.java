package dev.smp.eclipsepowers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The power roll, played out in the world instead of a menu.
 * <p>
 * Eight glowing orbs, one per power, circle the player. They spin fast and slow down until the
 * winner stops straight ahead, at the golden pointer. The big title shows whichever power is in
 * front right now, and the subtitle shows its neighbours, so the other powers you could get are
 * always in view. Then the winning orb flies into the player.
 */
public final class Roller {

    private static final int SPIN_TICKS = 150;       // 7.5 seconds of spinning
    private static final double RADIUS = 2.4;
    private static final double TWO_PI = Math.PI * 2;
    private static final int GOLD = 0xFFD43B;

    private final EclipsePowers plugin;
    private final PowerManager manager;
    private final Map<UUID, Roll> rolls = new HashMap<>();
    private final Set<UUID> waiting = new HashSet<>();

    public Roller(EclipsePowers plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public boolean isRolling(Player p) {
        return rolls.containsKey(p.getUniqueId()) || waiting.contains(p.getUniqueId());
    }

    /** Counts down in the action bar, then starts the roll. Used when a player joins without a power. */
    public void queueJoinRoll(Player p, int seconds) {
        UUID id = p.getUniqueId();
        if (rolls.containsKey(id) || !waiting.add(id)) return;

        Fx.loop(plugin, 20, i -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline() || manager.getPower(pl) != null) {
                waiting.remove(id);
                return false;
            }
            int left = seconds - i;
            if (left <= 0) {
                waiting.remove(id);
                start(pl, null);
                return false;
            }
            pl.sendActionBar(Component.text("Your power rolls in " + left + "...", NamedTextColor.GOLD));
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f + 0.1f * (seconds - left));
            return true;
        });
    }

    /** Starts the roll right now. forced = a fixed result, or null for a random one. */
    public void start(Player p, Power forced) {
        UUID id = p.getUniqueId();
        if (rolls.containsKey(id)) return;
        Power target = forced != null ? forced : manager.pickRandom(p);
        Roll roll = new Roll(id, target);
        rolls.put(id, roll);

        manager.shield(p, (SPIN_TICKS + 90) * 50L); // nothing can hurt you while you roll
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        Fx.expandingRing(plugin, p.getLocation().add(0, 0.1, 0), 0.3, RADIUS + 1, 8, GOLD, 1.5f, Particle.END_ROD);
        Fx.loop(plugin, 1, tick -> spin(roll, tick));
    }

    /** The player left mid-roll: give the rolled power right away so nothing is lost. */
    public void finishNow(Player p) {
        waiting.remove(p.getUniqueId());
        Roll roll = rolls.remove(p.getUniqueId());
        if (roll != null && !roll.assigned) {
            roll.assigned = true;
            manager.setPower(p, roll.target);
        }
    }

    // ------------------------------------------------------------------
    // the spin
    // ------------------------------------------------------------------

    private boolean spin(Roll roll, int tick) {
        if (rolls.get(roll.id) != roll) return false;
        Player p = Bukkit.getPlayer(roll.id);
        if (p == null || !p.isOnline()) return false;

        double x = Math.min(1.0, tick / (double) SPIN_TICKS);
        double eased = 1 - Math.pow(1 - x, 3);          // fast start, slow finish
        double rot = roll.total * eased;
        Power[] all = Power.values();
        int front = frontIndex(rot, all.length);

        drawCarousel(p, all, rot, front, tick);

        boolean landed = tick >= SPIN_TICKS;
        if (front != roll.lastFront && (tick - roll.lastTitleTick >= 2 || landed)) {
            roll.lastFront = front;
            roll.lastTitleTick = tick;
            showSpinTitle(p, all, front);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, (float) (0.6 + 1.2 * x));
            Fx.burst(ahead(p, RADIUS + 0.7, 1.25), Particle.END_ROD, 5, 0.1, 0.05);
        }
        if (landed) {
            reveal(roll, p, all);
            return false;
        }
        return true;
    }

    /** Which orb is closest to straight ahead after the carousel turned by rot. */
    private int frontIndex(double rot, int n) {
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            double diff = Math.abs(normalize(k * TWO_PI / n + rot));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = k;
            }
        }
        return best;
    }

    private static double normalize(double angle) {
        return angle - TWO_PI * Math.floor((angle + Math.PI) / TWO_PI);
    }

    private void drawCarousel(Player p, Power[] all, double rot, int front, int tick) {
        World w = p.getWorld();
        Location base = p.getLocation();
        double alpha = facingAngle(p);
        int n = all.length;
        int frontRgb = all[front].rgb();

        for (int k = 0; k < n; k++) {
            double rel = k * TWO_PI / n + rot;            // angle relative to straight ahead
            double theta = alpha + rel;
            double closeness = Math.max(0, Math.cos(rel));
            double c4 = closeness * closeness * closeness * closeness;
            double y = 1.25 + 0.12 * Math.sin(tick * 0.25 + k);
            int rgb = all[k].rgb();

            Location orb = new Location(w, base.getX() + Math.cos(theta) * RADIUS, base.getY() + y,
                    base.getZ() + Math.sin(theta) * RADIUS);
            Fx.sphereDust(orb, 0.16 + 0.26 * c4, 6, tick * 0.4, rgb, 1.1f + (float) (0.5 * c4));
            w.spawnParticle(Particle.END_ROD, orb, 1, 0.03, 0.03, 0.03, 0.0);

            // comet tail behind the orb
            for (int t = 1; t <= 3; t++) {
                double tt = theta - t * 0.13;
                Fx.dust(w, base.getX() + Math.cos(tt) * RADIUS, base.getY() + y,
                        base.getZ() + Math.sin(tt) * RADIUS, rgb, 1.0f - t * 0.2f);
            }
        }

        // the ground ring and a rising helix take the color of whatever is in front
        Location g = base.clone().add(0, 0.08, 0);
        Fx.ringDust(g, RADIUS, 28, frontRgb, 1.2f);
        Fx.ringDust(g, RADIUS - 0.5, 14, frontRgb, 0.9f);
        Fx.helix(base, 0.7, 2.4, 1.5, 16, tick * 0.35, frontRgb, 1.1f);

        // golden pointer straight ahead
        Location pointer = ahead(p, RADIUS + 0.7, 0);
        Fx.column(pointer, 2.4, 0.4, Particle.END_ROD);
        Fx.dustBurst(pointer.clone().add(0, 2.6, 0), GOLD, 1.6f, 4, 0.12);
    }

    private void showSpinTitle(Player p, Power[] all, int front) {
        int n = all.length;
        Component strip = Component.empty();
        for (int d = 2; d >= -2; d--) {
            Power pw = all[Math.floorMod(front + d, n)];
            if (d != 2) strip = strip.append(Component.text("  |  ", NamedTextColor.DARK_GRAY));
            strip = strip.append(d == 0
                    ? Component.text(pw.displayName().toUpperCase(Locale.ROOT), pw.color(), TextDecoration.BOLD)
                    : Component.text(pw.displayName(), NamedTextColor.DARK_GRAY));
        }
        Power current = all[front];
        p.showTitle(Title.title(
                Component.text(current.displayName(), current.color(), TextDecoration.BOLD),
                strip,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
    }

    // ------------------------------------------------------------------
    // the landing
    // ------------------------------------------------------------------

    private void reveal(Roll roll, Player p, Power[] all) {
        roll.assigned = true;
        Power target = roll.target;
        manager.setPower(p, target);

        World w = p.getWorld();
        int n = all.length;
        double alpha = facingAngle(p);
        Location base = p.getLocation();

        // the other orbs burst apart, the winner swells
        Location winner = null;
        for (int k = 0; k < n; k++) {
            double theta = alpha + k * TWO_PI / n + roll.total;
            Location orb = new Location(w, base.getX() + Math.cos(theta) * RADIUS, base.getY() + 1.25,
                    base.getZ() + Math.sin(theta) * RADIUS);
            if (all[k] == target) {
                winner = orb;
            } else {
                Fx.dustBurst(orb, all[k].rgb(), 1.6f, 25, 0.4);
                Fx.burst(orb, Particle.CLOUD, 6, 0.2, 0.05);
            }
        }
        if (winner == null) winner = ahead(p, RADIUS, 1.25);
        final Location start = winner;
        int rgb = target.rgb();
        Fx.sphereDust(start, 0.6, 40, 0, rgb, 1.8f);
        Fx.burst(start, Particle.FIREWORK, 40, 0.3, 0.2);
        Fx.expandingRing(plugin, base.clone().add(0, 0.1, 0), 0.5, RADIUS + 2, 8, rgb, 1.6f, Particle.END_ROD);
        p.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        p.showTitle(Title.title(
                Component.text(target.displayName(), target.color(), TextDecoration.BOLD),
                Component.text("You rolled " + target.displayName() + "!", NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(100))));

        // the winning orb flies into the player, then the full awaken effect plays
        Fx.run(plugin, 14, 1, j -> {
            Player pl = Bukkit.getPlayer(roll.id);
            if (pl == null || !pl.isOnline()) return;
            double t = (j + 1) / 14.0;
            Location end = pl.getLocation().add(0, 1.0, 0);
            Location control = new Location(w, (start.getX() + end.getX()) / 2,
                    Math.max(start.getY(), end.getY()) + 1.5, (start.getZ() + end.getZ()) / 2);
            for (int k = 0; k < 4; k++) {
                Location pos = Fx.bezier(start, control, end, Math.max(0, t - k * 0.05));
                w.spawnParticle(Particle.END_ROD, pos, 1, 0.03, 0.03, 0.03, 0.0);
                Fx.dust(pos, rgb, 1.8f - k * 0.3f);
            }
            if (j == 13) {
                rolls.remove(roll.id);
                manager.awaken(pl, target);
            }
        });
    }

    /** The direction the player is looking along the ground, as an angle. */
    private static double facingAngle(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) return Math.PI / 2;
        return Math.atan2(dir.getZ(), dir.getX());
    }

    private static Location ahead(Player p, double distance, double height) {
        double alpha = facingAngle(p);
        Location b = p.getLocation();
        return new Location(p.getWorld(), b.getX() + Math.cos(alpha) * distance, b.getY() + height,
                b.getZ() + Math.sin(alpha) * distance);
    }

    private static final class Roll {
        final UUID id;
        final Power target;
        /** Total angle the carousel turns: five full turns plus whatever puts the winner in front. */
        final double total;
        int lastFront = -1;
        int lastTitleTick = -10;
        boolean assigned = false;

        Roll(UUID id, Power target) {
            this.id = id;
            this.target = target;
            double baseAngle = target.ordinal() * TWO_PI / Power.values().length;
            double toFront = ((-baseAngle) % TWO_PI + TWO_PI) % TWO_PI;
            this.total = TWO_PI * 5 + toFront;
        }
    }
}
