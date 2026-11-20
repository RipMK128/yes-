package dev.smp.eclipsepowers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every ability returns true if it was used (cooldown starts) and false if it
 * could not be used (no target etc., no cooldown).
 */
public final class Abilities {

    // colors (0xRRGGBB) used by the dust particles
    private static final int VOID = 0x8A2BE2, VOID_LT = 0xD9B8FF, VOID_BLACK = 0x050008;
    private static final int NULL_W = 0xE5E7EB, NULL_G = 0x9AA0A6, NULL_SILVER = 0xCFD3D8;
    private static final int LIGHT_Y = 0xFFE066, LIGHT_W = 0xFFF8D6, SUN = 0xFFD43B;
    private static final int DARK_P = 0x4C3F8F, DARK_B = 0x0B0614;
    private static final int SHADOW_P = 0x5B4D73, SHADOW_D = 0x2B2540, SHADOW_L = 0xE9D5FF;
    private static final int ECL_O = 0xFF9F1C, ECL_Y = 0xFFCF70, ECL_B = 0x050505;
    private static final int ASTRAL_C = 0x4CC9F0, WHITE = 0xFFFFFF;
    private static final double TWO_PI = Math.PI * 2;
    private static final int[] CHAOS = {0xF72585, 0x7209B7, 0x4CC9F0, 0xFACC15, 0x4ADE80, 0xFF6B35};

    private final EclipsePowers plugin;
    private final PowerManager manager;
    private final Map<UUID, Long> lastRipple = new HashMap<>();
    private String targetMode = "hostile";
    private boolean auraFancy = true;

    public Abilities(EclipsePowers plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        reloadSettings();
    }

    /** Re-reads settings that are cached for speed. */
    public void reloadSettings() {
        targetMode = plugin.getConfig().getString("target-mode", "hostile").toLowerCase(Locale.ROOT);
        auraFancy = !"subtle".equalsIgnoreCase(plugin.getConfig().getString("aura-style", "fancy"));
    }

    /** Damage amounts live in config.yml under "damage:" (in half hearts, 2 = 1 heart). */
    private double dmg(String key, double fallback) {
        return Math.max(0, plugin.getConfig().getDouble("damage." + key, fallback));
    }

    public boolean cast(Player p, Power power, int slot) {
        if (power.ordinal() >= Power.NATURE.ordinal()) {
            return NewPowers.cast(plugin, manager, p, power, slot);
        }
        boolean first = slot == 1;
        boolean ok = switch (power) {
            case VOID -> first ? voidPull(p) : voidRift(p);
            case NULL -> first ? nullPulse(p) : nullShield(p);
            case LIGHT -> first ? lightBeam(p) : solarFlare(p);
            case DARK -> first ? darkVeil(p) : soulDrain(p);
            case SHADOW -> first ? shadowStep(p) : shadowStrike(p);
            case ECLIPSE -> first ? eclipseFlash(p) : totality(p);
            case ASTRAL -> first ? astralDash(p) : starFall(p);
            case CHAOS -> first ? chaosRoll(p) : chaosStorm(p);
        };
        if (ok) flourish(p, power, slot);
        return ok;
    }

    /** Signature burst in the power's color on every successful cast. Bigger for ability 2. */
    private void flourish(Player p, Power power, int slot) {
        int rgb = power.rgb();
        World w = p.getWorld();
        Location b = p.getLocation();
        double big = slot == 2 ? 1.0 : 0.0;

        Fx.expandingRing(plugin, b.clone().add(0, 0.1, 0), 0.3, 2.0 + big * 1.4, 5, rgb, 1.4f, null);
        Fx.expandingRing(plugin, b.clone().add(0, 1.0, 0), 0.3, 1.6 + big, 6, WHITE, 1.1f, null);
        Fx.burst(b.clone().add(0, 1, 0), Particle.END_ROD, 12 + (int) (big * 14), 0.3, 0.06);
        Fx.dustBurst(b.clone().add(0, 1, 0), rgb, 1.4f, 16, 0.45);
        Fx.run(plugin, 8, 1, i -> {
            if (!p.isOnline()) return;
            Fx.helix(p.getLocation(), 0.7 + big * 0.2, 2.2, 1.2, 14, i * 0.9, rgb, 1.2f);
        });
        if (slot == 2) {
            Fx.burst(b.clone().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 20, 0.5, 0.3);
            Fx.column(b, 6, 0.4, Particle.END_ROD);
            w.playSound(b, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
        }
    }

    /** A fallen victim's soul rises, then streams into the killer in the killer's power color. */
    public void killEffect(Player killer, Location where, Power power) {
        int rgb = power.rgb();
        World w = where.getWorld();
        Location c = where.clone().add(0, 1, 0);
        Fx.burst(c, Particle.SOUL, 30, 0.4, 0.1);
        Fx.dustBurst(c, rgb, 1.8f, 40, 0.7);
        Fx.expandingRing(plugin, where.clone().add(0, 0.1, 0), 0.3, 3.5, 7, rgb, 1.5f, Particle.END_ROD);
        Fx.helix(where, 0.6, 3, 2, 30, 0, rgb, 1.3f);
        Fx.debris(where.clone().add(0, 0.3, 0), 20);
        w.playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);

        Fx.run(plugin, 14, 1, i -> {
            if (!killer.isOnline()) return;
            double t = (i + 1) / 14.0;
            Location end = chest(killer);
            Location ctrl = new Location(w, (c.getX() + end.getX()) / 2,
                    Math.max(c.getY(), end.getY()) + 2.5, (c.getZ() + end.getZ()) / 2);
            for (int k = 0; k < 3; k++) {
                Location pos = Fx.bezier(c, ctrl, end, Math.max(0, t - k * 0.06));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 1, 0.05, 0.05, 0.05, 0.01);
                Fx.dust(pos, rgb, 1.6f - k * 0.3f);
            }
            if (i == 13) {
                Fx.burst(end, Particle.END_ROD, 20, 0.4, 0.1);
                Fx.sphereDust(end, 0.8, 24, 0, rgb, 1.4f);
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
        });
    }

    // =====================================================================
    // VOID
    // =====================================================================

    private boolean voidPull(Player p) {
        World w = p.getWorld();
        Location base = p.getLocation();

        List<LivingEntity> targets = enemies(p, 9);
        if (nobodyAround(p, targets)) return false;

        for (LivingEntity e : targets) {
            Dmg.hit(e, p, dmg("void-pull", 3));
            pull(e, base, 1.2, 0.3);
            Location from = chest(e);
            curve(from, chest(p), 1.5, VOID, 1.4f);
            curve(from.clone().add(0, 0.4, 0), chest(p).add(0, 0.3, 0), -1.0, VOID_LT, 1.0f);
            Fx.burst(from, Particle.REVERSE_PORTAL, 30, 0.4, 0.4);
            Fx.dustBurst(from, VOID, 1.6f, 15, 0.5);
        }

        // shockwave collapsing inward along the ground
        Fx.run(plugin, 10, 1, i -> {
            double r = 9 - 8.0 * (i / 9.0);
            Location g = p.getLocation().add(0, 0.12, 0);
            Fx.ringDust(g, r, (int) (r * 9), VOID, 1.5f);
            Fx.ring(g, r, (int) (r * 3), Particle.REVERSE_PORTAL);
            Fx.ringDust(g.clone().add(0, 0.5 + i * 0.12, 0), r * 0.6, (int) (r * 5), VOID_LT, 1.0f);
        });

        // three vortex arms spiralling up around the caster
        Fx.run(plugin, 16, 1, i -> {
            Location b = p.getLocation();
            for (int arm = 0; arm < 3; arm++) {
                double a = i * 0.55 + arm * (2 * Math.PI / 3);
                double rad = 3.2 - i * 0.16;
                double x = b.getX() + Math.cos(a) * rad;
                double y = b.getY() + 0.2 + i * 0.14;
                double z = b.getZ() + Math.sin(a) * rad;
                Fx.dust(w, x, y, z, VOID, 1.5f);
                w.spawnParticle(Particle.PORTAL, x, y, z, 2, 0.1, 0.1, 0.1, 0.1);
            }
        });

        Fx.burst(base.clone().add(0, 1, 0), Particle.PORTAL, 120, 1.5, 0.8);
        Fx.sphereDust(base.clone().add(0, 1, 0), 1.2, 40, 0, VOID_BLACK, 1.8f);
        w.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        w.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        return true;
    }

    private void pull(LivingEntity e, Location to, double strength, double lift) {
        Vector v = to.toVector().subtract(e.getLocation().toVector());
        if (v.lengthSquared() < 0.25) return;
        v.normalize().multiply(strength);
        v.setY(e.getVelocity().getY() + lift);
        e.setVelocity(v);
    }

    private void heal(Player p, double amount) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20.0 : max.getValue();
        p.setHealth(Math.min(cap, p.getHealth() + amount));
    }
}
