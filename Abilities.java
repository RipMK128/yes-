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

    private boolean voidRift(Player p) {
        Location center = targetLocation(p, 20).add(0, 1.2, 0);
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);

        // the rift tears open: ground ring, aim beam
        Fx.expandingRing(plugin, center.clone().add(0, -1.1, 0), 0.5, 6, 10, VOID, 1.6f, Particle.REVERSE_PORTAL);
        Fx.spokes(center.clone().add(0, -1.1, 0), 6, 10, VOID, 1.2f);
        Fx.debris(center.clone().add(0, -0.9, 0), 40);
        Fx.lineDust(chest(p), center, 0.5, VOID_LT, 0.9f);

        Fx.loop(plugin, 2, i -> {
            int ticks = i * 2;
            if (ticks >= 80 || !p.isOnline()) {
                collapse(center);
                return false;
            }
            double spin = ticks * 0.18;

            // black core with a glowing rim
            Fx.sphereDust(center, 0.9, 30, spin, VOID_BLACK, 2.2f);
            Fx.sphereDust(center, 1.25, 24, -spin, VOID, 1.2f);

            // tilted accretion disks, spinning in opposite directions
            for (int k = 0; k < 36; k++) {
                double a = spin * 1.5 + k * Math.PI * 2 / 36;
                double x = Math.cos(a) * 2.6;
                double z = Math.sin(a) * 2.6;
                Fx.dust(w, center.getX() + x, center.getY() + z * Math.sin(0.55),
                        center.getZ() + z * Math.cos(0.55), k % 2 == 0 ? VOID : VOID_LT, 1.2f);
            }
            for (int k = 0; k < 20; k++) {
                double a = -spin * 2.2 + k * Math.PI * 2 / 20;
                double x = Math.cos(a) * 1.8;
                double z = Math.sin(a) * 1.8;
                Fx.dust(w, center.getX() + x, center.getY() - z * Math.sin(0.3),
                        center.getZ() + z * Math.cos(0.3), VOID_LT, 0.9f);
            }

            // matter streaming in from the edge of the damage radius
            w.spawnParticle(Particle.PORTAL, center, Fx.n(40), 3, 3, 3, 1.0);
            w.spawnParticle(Particle.REVERSE_PORTAL, center, Fx.n(10), 0.6, 0.6, 0.6, 0.05);

            // arcs of energy snapping into the core
            for (int k = 0; k < 2; k++) {
                Location rim = center.clone().add(randomUnit().multiply(3.5 + Math.random() * 2));
                Fx.lineDust(rim, center, 0.35, VOID_LT, 0.8f);
            }
            if (ticks % 10 == 0) {
                Fx.ringDust(center.clone().add(0, -1.0, 0), 6, 40, VOID, 1.0f);
            }

            for (Entity e : w.getNearbyEntities(center, 6, 6, 6)) {
                if (!isTarget(p, e)) continue;
                LivingEntity le = (LivingEntity) e;
                if (ticks % 10 == 0) Dmg.hit(le, p, dmg("void-rift-per-hit", 1.5));
                pull(le, center, 0.5, 0.0);
                if (ticks % 4 == 0) Fx.lineDust(chest(le), center, 0.8, VOID, 1.0f);
            }
            return true;
        });
        return true;
    }

    private void collapse(Location center) {
        World w = center.getWorld();
        Fx.run(plugin, 6, 1, i -> Fx.sphereDust(center, 3.0 - i * 0.5, 60, i, VOID, 1.4f));
        Fx.later(plugin, 6, () -> {
            Fx.burst(center, Particle.REVERSE_PORTAL, 160, 1.0, 0.7);
            Fx.burst(center, Particle.FIREWORK, 60, 0.5, 0.25);
            Fx.expandingRing(plugin, center, 0.5, 7, 8, VOID, 1.8f, Particle.PORTAL);
            w.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
        });
    }

    // =====================================================================
    // NULL
    // =====================================================================

    private boolean nullPulse(Player p) {
        World w = p.getWorld();
        Location g = p.getLocation().add(0, 0.15, 0);
        List<LivingEntity> targets = enemies(p, 9);
        if (nobodyAround(p, targets)) return false;

        // three stacked shockwave rings and an expanding shell
        Fx.expandingRing(plugin, g, 0.5, 9, 9, NULL_W, 1.7f, Particle.ELECTRIC_SPARK);
        Fx.expandingRing(plugin, g.clone().add(0, 0.9, 0), 0.5, 9, 11, NULL_G, 1.3f, null);
        Fx.expandingRing(plugin, g.clone().add(0, 1.8, 0), 0.5, 9, 13, NULL_W, 1.0f, null);
        Fx.run(plugin, 7, 1, i ->
                Fx.sphereDust(p.getLocation().add(0, 1, 0), 1.0 + i * 1.3, 70, i * 0.4, NULL_SILVER, 1.1f));

        for (LivingEntity e : targets) {
            for (PotionEffect effect : new ArrayList<>(e.getActivePotionEffects())) {
                e.removePotionEffect(effect.getType());
            }
            if (e instanceof Player target) {
                manager.silence(target, 6000);
                target.sendMessage(Component.text("Null magic has silenced you for 6 seconds.",
                        NamedTextColor.GRAY));
            }
            Location c = chest(e);
            Fx.burst(c, Particle.ELECTRIC_SPARK, 40, 0.4, 0.2);
            Fx.burst(c, Particle.SMOKE, 25, 0.3, 0.03);
            Fx.ringDust(c, 0.7, 16, NULL_W, 1.2f);
            Fx.ringDust(c.clone().add(0, 0.5, 0), 0.5, 12, NULL_G, 1.0f);
            markX(e, p, 0xEF4444, 20);
            Dmg.hit(e, p, dmg("null-pulse", 3));
        }
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
        return true;
    }

    /** A red X hovering over a silenced target. */
    private void markX(LivingEntity target, Player caster, int rgb, int ticks) {
        Fx.run(plugin, ticks / 2, 2, i -> {
            if (!target.isValid()) return;
            Vector toCaster = caster.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (toCaster.lengthSquared() < 0.01) toCaster = new Vector(0, 0, 1);
            toCaster.normalize();
            Vector right = new Vector(-toCaster.getZ(), 0, toCaster.getX());
            Location c = target.getEyeLocation().add(0, 0.7, 0);
            Vector a = right.clone().multiply(0.35).add(new Vector(0, 0.35, 0));
            Vector b = right.clone().multiply(0.35).add(new Vector(0, -0.35, 0));
            Fx.lineDust(c.clone().subtract(a), c.clone().add(a), 0.12, rgb, 1.0f);
            Fx.lineDust(c.clone().subtract(b), c.clone().add(b), 0.12, rgb, 1.0f);
        });
    }

    private boolean nullShield(Player p) {
        manager.shield(p, 4000);
        World w = p.getWorld();
        Location c0 = p.getLocation();
        w.playSound(c0, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
        Fx.expandingRing(plugin, c0.clone().add(0, 0.1, 0), 0.3, 3, 6, NULL_W, 1.4f, Particle.END_ROD);

        Fx.loop(plugin, 2, i -> {
            int ticks = i * 2;
            if (!p.isOnline() || ticks >= 80) {
                if (p.isOnline()) shatterShield(p);
                return false;
            }
            Location c = p.getLocation().add(0, 1, 0);
            double spin = ticks * 0.12;
            Fx.sphereDust(c, 1.55, 44, spin, NULL_W, 1.0f);
            Fx.sphereDust(c, 1.45, 20, -spin, NULL_G, 0.8f);
            Fx.ringDust(c, 1.6, 24, WHITE, 1.2f);
            Fx.ringDust(p.getLocation().add(0, 0.05, 0), 1.9, 30, NULL_G, 1.0f);
            if (ticks % 6 == 0) {
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, Fx.n(6), 1.2, 1.2, 1.2, 0.05);
            }
            return true;
        });
        return true;
    }

    private void shatterShield(Player p) {
        World w = p.getWorld();
        Fx.run(plugin, 4, 1, i ->
                Fx.sphereDust(p.getLocation().add(0, 1, 0), 1.6 + i * 0.7, 60, i, NULL_W, 1.4f - i * 0.2f));
        Fx.burst(p.getLocation().add(0, 1, 0), Particle.END_ROD, 40, 0.8, 0.15);
        Fx.burst(p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 40, 0.8, 0.2);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
    }

    /** Called when the shield absorbs a hit. */
    public void shieldRipple(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastRipple.get(p.getUniqueId());
        if (last != null && now - last < 200) return;
        lastRipple.put(p.getUniqueId(), now);

        Location c = p.getLocation().add(0, 1, 0);
        Location spot = c.clone().add(randomUnit().multiply(1.5));
        Fx.burst(spot, Particle.END_ROD, 8, 0.15, 0.04);
        Fx.burst(spot, Particle.ELECTRIC_SPARK, 14, 0.25, 0.1);
        Fx.run(plugin, 4, 1, i ->
                Fx.sphereDust(p.getLocation().add(0, 1, 0), 1.5 + i * 0.12, 40, i, WHITE, 1.4f - i * 0.25f));
        p.getWorld().playSound(c, Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
    }

    // =====================================================================
    // LIGHT
    // =====================================================================

    private boolean lightBeam(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult hit = w.rayTrace(eye, dir, 25, FluidCollisionMode.NEVER, true, 0.5,
                e -> isTarget(p, e));
        double length = hit == null ? 25 : eye.toVector().distance(hit.getHitPosition());

        Vector up = Math.abs(dir.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(up).normalize();
        Vector up2 = right.clone().crossProduct(dir).normalize();

        // muzzle flash
        Location muzzle = eye.clone().add(dir.clone().multiply(1.0));
        Fx.burst(muzzle, Particle.END_ROD, 25, 0.25, 0.08);
        Fx.sphereDust(muzzle, 0.5, 18, 0, LIGHT_W, 1.4f);
        Fx.expandingRing(plugin, p.getLocation().add(0, 0.1, 0), 0.3, 2, 5, LIGHT_Y, 1.2f, null);

        // beam: bright core plus a double helix wrapped around it
        List<Location> glow = new ArrayList<>();
        for (double d = 0.8; d <= length; d += 0.25) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            if (Math.round(d / 0.25) % 4 == 0) glow.add(pt);
            w.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
            double a = d * 2.4;
            Vector off = right.clone().multiply(Math.cos(a) * 0.32).add(up2.clone().multiply(Math.sin(a) * 0.32));
            Fx.dust(w, pt.getX() + off.getX(), pt.getY() + off.getY(), pt.getZ() + off.getZ(), LIGHT_Y, 1.1f);
            Fx.dust(w, pt.getX() - off.getX(), pt.getY() - off.getY(), pt.getZ() - off.getZ(), LIGHT_W, 0.9f);
        }

        // the beam leaves drifting sparks behind for a second
        Fx.run(plugin, 10, 2, i -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (Location pt : glow) {
                if (rnd.nextInt(3) != 0) continue;
                w.spawnParticle(Particle.END_ROD, pt.getX() + rnd.nextDouble(-0.25, 0.25),
                        pt.getY() + rnd.nextDouble(-0.25, 0.25) + i * 0.04, pt.getZ() + rnd.nextDouble(-0.25, 0.25),
                        1, 0, 0, 0, 0.01);
            }
        });

        // impact
        Location end = eye.clone().add(dir.clone().multiply(length));
        Fx.burst(end, Particle.FIREWORK, 40, 0.3, 0.12);
        Fx.sphereDust(end, 0.8, 30, 0, LIGHT_W, 1.5f);
        Fx.expandingRing(plugin, end, 0.3, 3.2, 6, LIGHT_Y, 1.5f, Particle.END_ROD);
        Fx.column(end, 5, 0.3, Particle.END_ROD);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            Dmg.hit(target, p, dmg("light-beam", 8));
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            Fx.burst(chest(target), Particle.END_ROD, 30, 0.4, 0.2);
        }
        w.playSound(eye, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        w.playSound(end, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.2f);
        return true;
    }

    private boolean solarFlare(Player p) {
        World w = p.getWorld();
        for (LivingEntity e : enemies(p, 8)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
            e.setFireTicks(80);
            Dmg.hit(e, p, dmg("solar-flare", 6));
            Fx.burst(chest(e), Particle.FLAME, 30, 0.35, 0.05);
            Fx.dustBurst(chest(e), WHITE, 1.8f, 20, 0.5);
            Fx.ringDust(e.getLocation().add(0, 0.1, 0), 1.2, 16, 0xFF922B, 1.3f);
        }
        heal(p, 6);
        Fx.spiralUp(plugin, p, 24, 0.9, 0x4ADE80, 1.2f, Particle.HAPPY_VILLAGER);

        // a small sun forms above the caster and erupts into rays
        Fx.run(plugin, 14, 1, i -> {
            if (!p.isOnline()) return;
            Location b = p.getLocation();
            double t = (i + 1) / 14.0;
            Location sun = b.clone().add(0, 3.3, 0);
            Fx.sphereDust(sun, 0.45 + 0.35 * t, 36, i * 0.4, SUN, 1.7f);
            Fx.sphereDust(sun, 0.25, 12, 0, LIGHT_W, 1.5f);
            w.spawnParticle(Particle.END_ROD, sun, Fx.n(4), 0.5, 0.5, 0.5, 0.05);

            double len = 9 * t;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8 + t * 0.6;
                for (double d = Math.max(1, len - 2); d <= len; d += 0.6) {
                    Fx.dust(w, b.getX() + Math.cos(a) * d, b.getY() + 0.3, b.getZ() + Math.sin(a) * d,
                            k % 2 == 0 ? LIGHT_Y : LIGHT_W, 1.2f);
                }
            }
            if (i < 8) Fx.column(b, 3 + i, 0.5, Particle.END_ROD);
            Fx.ringDust(b.clone().add(0, 0.1, 0), len, (int) (len * 8), SUN, 1.4f);
        });
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.8f);
        return true;
    }

    // =====================================================================
    // DARK
    // =====================================================================

    private boolean darkVeil(Player p) {
        World w = p.getWorld();
        Location c = p.getLocation().add(0, 1, 0);

        // a black cloud swells outward from the caster
        Fx.run(plugin, 10, 1, i -> {
            if (!p.isOnline()) return;
            Location pc = p.getLocation().add(0, 1, 0);
            Fx.sphereDust(pc, 1 + i * 0.85, 40 + i * 12, i * 0.2, DARK_B, 2.0f);
            w.spawnParticle(Particle.LARGE_SMOKE, pc, Fx.n(10), 1 + i * 0.5, 0.6, 1 + i * 0.5, 0.01);
            Fx.ringDust(p.getLocation().add(0, 0.1, 0), 1 + i * 0.85, 30 + i * 6, DARK_P, 1.3f);
        });

        for (LivingEntity e : enemies(p, 9)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
            Dmg.hit(e, p, dmg("dark-veil", 3));
            Location ec = chest(e);
            curve(c, ec, 1.2, 0x2B1D4D, 1.3f);
            curve(c, ec, -0.8, DARK_P, 1.0f);
            Fx.sphereDust(ec, 0.9, 30, 0, DARK_B, 1.5f);
            w.spawnParticle(Particle.SQUID_INK, ec, Fx.n(30), 0.4, 0.6, 0.4, 0.02);
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

        // afterimages and ink left behind while the speed lasts
        Fx.run(plugin, 25, 2, i -> {
            if (!p.isOnline()) return;
            Location l = p.getLocation().add(0, 0.9, 0);
            w.spawnParticle(Particle.SQUID_INK, l, Fx.n(3), 0.25, 0.5, 0.25, 0.01);
            Fx.dustBurst(l, DARK_P, 1.5f, 6, 0.3);
            if (i % 3 == 0) Fx.silhouette(p.getLocation(), rightOf(p), DARK_P, 0.9f);
        });
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.6f);
        w.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1f, 0.5f);
        return true;
    }

    private boolean soulDrain(Player p) {
        World w = p.getWorld();
        List<LivingEntity> hit = enemies(p, 7);
        if (nobodyAround(p, hit)) return false;
        double healed = 0;
        for (LivingEntity e : hit) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            Dmg.hit(e, p, dmg("soul-drain", 5));
            if (healed < 6) {
                heal(p, 2);
                healed += 2;
            }
            Location ec = chest(e);
            Fx.burst(ec, Particle.SOUL, 25, 0.35, 0.05);
            Fx.ringDust(e.getLocation().add(0, 0.1, 0), 1.1, 16, 0x7F1D1D, 1.2f);
            Fx.dustBurst(ec, 0x000000, 1.6f, 8, 0.4);
        }

        // souls ripped out of the victims and streaming into the caster
        Fx.run(plugin, 14, 1, i -> {
            if (!p.isOnline()) return;
            double t = (i + 1) / 14.0;
            Location end = chest(p);
            for (LivingEntity e : hit) {
                Location start = chest(e);
                Location ctrl = new Location(w, (start.getX() + end.getX()) / 2,
                        (start.getY() + end.getY()) / 2 + 2.5, (start.getZ() + end.getZ()) / 2);
                for (int k = 0; k < 3; k++) {
                    Location pos = Fx.bezier(start, ctrl, end, Math.max(0, t - k * 0.06));
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 1, 0.05, 0.05, 0.05, 0.01);
                    Fx.dust(pos, 0x5EEAD4, 1.5f - k * 0.3f);
                }
            }
        });
        Fx.spiralUp(plugin, p, 24, 0.8, 0x4ADE80, 1.2f, Particle.SOUL_FIRE_FLAME);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), Fx.n(40), 0.6, 0.8, 0.6, 0.05);
        w.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.2f);
        w.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
        return true;
    }

    // =====================================================================
    // SHADOW
    // =====================================================================

    private boolean shadowStep(Player p) {
        World w = p.getWorld();
        Location start = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection();

        Location best = start.clone();
        for (double d = 1; d <= 10; d += 0.5) {
            Location check = start.clone().add(dir.clone().multiply(d));
            if (check.getBlock().isPassable() && check.clone().add(0, 1, 0).getBlock().isPassable()) {
                best = check;
            } else {
                break;
            }
        }

        if (best.distanceSquared(start) < 1) {
            p.sendActionBar(Component.text("Something is blocking the way", NamedTextColor.GRAY));
            return false;
        }
        Location dest = best.clone();

        // a shadow clone stays behind where the caster stood
        Vector right = rightOf(p);
        Location ghost = start.clone();
        Fx.run(plugin, 8, 2, i -> Fx.silhouette(ghost, right, SHADOW_P, 1.0f));

        // smoke burst at the start, ink trail along the path, and a burst at the end
        Fx.burst(start.clone().add(0, 1, 0), Particle.SQUID_INK, 50, 0.4, 0.05);
        Fx.dustBurst(start.clone().add(0, 1, 0), SHADOW_D, 1.8f, 30, 0.5);
        Fx.expandingRing(plugin, start.clone().add(0, 0.1, 0), 0.3, 2, 6, SHADOW_P, 1.4f, null);
        double dist = start.distance(dest);
        for (double d = 0; d <= dist; d += 0.5) {
            Location pt = start.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
            w.spawnParticle(Particle.SQUID_INK, pt, 2, 0.2, 0.3, 0.2, 0.01);
            Fx.dustBurst(pt, SHADOW_D, 1.8f, 3, 0.25);
            if (d % 1.5 == 0) w.spawnParticle(Particle.WITCH, pt, 2, 0.2, 0.3, 0.2, 0.01);
        }

        p.teleport(dest);
        Fx.burst(dest.clone().add(0, 1, 0), Particle.SQUID_INK, 50, 0.4, 0.05);
        Fx.burst(dest.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 40, 0.4, 0.3);
        Fx.dustBurst(dest.clone().add(0, 1, 0), SHADOW_L, 1.2f, 20, 0.5);
        Fx.expandingRing(plugin, dest.clone().add(0, 0.1, 0), 0.3, 2.5, 6, SHADOW_P, 1.4f, Particle.WITCH);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
        return true;
    }

    private boolean shadowStrike(Player p) {
        LivingEntity target = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity e : enemies(p, 15)) {
            if (!p.hasLineOfSight(e)) continue;
            double d = e.getLocation().distanceSquared(p.getLocation());
            if (d < bestDistance) {
                bestDistance = d;
                target = e;
            }
        }
        if (target == null) {
            p.sendActionBar(Component.text("No target in range", NamedTextColor.GRAY));
            return false;
        }

        World w = p.getWorld();
        Location tl = target.getLocation();
        Vector facing = tl.getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) facing = new Vector(0, 0, 1);
        facing.normalize();

        Location dest = tl.clone().subtract(facing.multiply(1.5));
        dest.setY(tl.getY());
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            dest = tl.clone();
        }
        Vector look = tl.toVector().subtract(dest.toVector());
        if (look.lengthSquared() > 0.01) dest.setDirection(look);

        // shadow clone left at the start, dark streak between start and target
        Location from = p.getLocation();
        Vector right = rightOf(p);
        Fx.run(plugin, 8, 2, i -> Fx.silhouette(from, right, SHADOW_P, 1.0f));
        Fx.burst(from.clone().add(0, 1, 0), Particle.SQUID_INK, 50, 0.4, 0.05);
        curve(chest(p), chest(target), 2.0, SHADOW_D, 1.8f);

        p.teleport(dest);
        Fx.burst(dest.clone().add(0, 1, 0), Particle.SQUID_INK, 50, 0.4, 0.05);
        Fx.expandingRing(plugin, dest.clone().add(0, 0.1, 0), 0.3, 3, 6, SHADOW_P, 1.4f, null);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);

        Dmg.hit(target, p, dmg("shadow-strike", 10));
        slash(target, dest);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
        return true;
    }

    /** Three crossing slashes through the target. */
    private void slash(LivingEntity target, Location attacker) {
        World w = target.getWorld();
        Location c = chest(target);
        Vector toAttacker = attacker.toVector().subtract(target.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() < 0.01) toAttacker = new Vector(0, 0, 1);
        toAttacker.normalize();
        Vector right = new Vector(-toAttacker.getZ(), 0, toAttacker.getX());

        double[] tilts = {0.7, -0.6, 0.1};
        for (double tilt : tilts) {
            for (double s = -1.3; s <= 1.3; s += 0.1) {
                double bow = (1 - (s * s) / 1.7) * 0.35;
                Fx.dust(w, c.getX() + right.getX() * s * Math.cos(tilt) + toAttacker.getX() * bow,
                        c.getY() + s * Math.sin(tilt),
                        c.getZ() + right.getZ() * s * Math.cos(tilt) + toAttacker.getZ() * bow,
                        s > 0 ? SHADOW_L : WHITE, 1.4f);
            }
        }
        w.spawnParticle(Particle.SWEEP_ATTACK, c, 3, 0.4, 0.4, 0.4, 0);
        Fx.burst(c, Particle.DAMAGE_INDICATOR, 8, 0.3, 0.1);
        Fx.burst(c, Particle.SQUID_INK, 30, 0.3, 0.1);
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
    }

    // =====================================================================
    // ECLIPSE
    // =====================================================================

    private boolean eclipseFlash(Player p) {
        World w = p.getWorld();
        List<LivingEntity> targets = enemies(p, 10);
        if (nobodyAround(p, targets)) return false;
        for (LivingEntity e : targets) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            Dmg.hit(e, p, dmg("eclipse-flash", 4));
            Fx.burst(chest(e), Particle.END_ROD, 25, 0.4, 0.1);
            w.spawnParticle(Particle.SQUID_INK, chest(e), Fx.n(25), 0.4, 0.6, 0.4, 0.02);
            Fx.ringDust(chest(e), 0.8, 16, ECL_Y, 1.3f);
        }
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);

        // an eclipse forms above the caster
        Fx.run(plugin, 12, 1, i -> {
            if (!p.isOnline()) return;
            Location sky = p.getLocation().add(0, 3.6, 0);
            double grow = Math.min(1.0, (i + 1) / 6.0);
            Fx.sphereDust(sky, 0.75 * grow, 40, i * 0.3, ECL_B, 2.4f);
            Fx.sphereDust(sky, 1.1 * grow, 34, -i * 0.3, ECL_O, 1.2f);
            Fx.sphereDust(sky, 1.5 * grow, 20, i * 0.2, ECL_Y, 0.9f);
            for (int k = 0; k < 5; k++) {
                Vector d = randomUnit();
                Fx.lineDust(sky.clone().add(d.clone().multiply(1.1 * grow)),
                        sky.clone().add(d.clone().multiply(1.7 * grow)), 0.2, ECL_Y, 0.9f);
            }
        });

        // then it flares: rings, shell and sparks
        Fx.later(plugin, 6, () -> {
            Location b = p.getLocation();
            Fx.expandingRing(plugin, b.clone().add(0, 0.15, 0), 0.5, 10, 9, ECL_Y, 1.8f, Particle.END_ROD);
            Fx.expandingRing(plugin, b.clone().add(0, 1.1, 0), 0.5, 10, 11, WHITE, 1.5f, null);
            Fx.expandingRing(plugin, b.clone().add(0, 2.1, 0), 0.5, 10, 13, ECL_O, 1.3f, null);
            Fx.burst(b.clone().add(0, 1, 0), Particle.FIREWORK, 100, 1.2, 0.3);
            Fx.burst(b.clone().add(0, 1, 0), Particle.END_ROD, 80, 0.5, 0.4);
            Fx.run(plugin, 6, 1, i ->
                    Fx.sphereDust(p.getLocation().add(0, 1, 0), 1 + i * 1.6, 90, i, WHITE, 1.6f));
            w.playSound(b, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.6f);
            w.playSound(b, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
        });
        return true;
    }

    private boolean totality(Player p) {
        World w = p.getWorld();
        List<LivingEntity> victims = enemies(p, 9);
        if (nobodyAround(p, victims)) return false;
        for (LivingEntity e : victims) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 6));
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
            w.spawnParticle(Particle.SQUID_INK, chest(e), Fx.n(40), 0.4, 0.7, 0.4, 0.02);
        }

        Location origin = p.getLocation();
        Location sky = origin.clone().add(0, 7, 0);
        Fx.expandingRing(plugin, origin.clone().add(0, 0.15, 0), 0.5, 9, 10, ECL_O, 1.8f, Particle.END_ROD);
        Fx.spokes(origin.clone().add(0, 0.12, 0), 9, 12, ECL_O, 1.2f);
        Fx.debris(origin.clone().add(0, 0.3, 0), 50);
        w.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
        w.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.5f);

        // a full eclipse hangs overhead for the whole 4 seconds
        Fx.run(plugin, 40, 2, i -> {
            double grow = Math.min(1.0, (i + 1) / 8.0);
            double spin = i * 0.2;
            Fx.sphereDust(sky, 2.0 * grow, 70, spin, ECL_B, 2.8f);
            Fx.sphereDust(sky, 2.6 * grow, 50, -spin, ECL_O, 1.3f);
            Fx.sphereDust(sky, 3.3 * grow, 30, spin, ECL_Y, 0.9f);
            for (int k = 0; k < 6; k++) {
                Vector d = randomUnit();
                Fx.lineDust(sky.clone().add(d.clone().multiply(2.6 * grow)),
                        sky.clone().add(d.clone().multiply(4.2 * grow)), 0.25, ECL_Y, 1.0f);
            }
            if (i % 2 == 0) Fx.ringDust(origin.clone().add(0, 0.12, 0), 9, 70, ECL_O, 1.3f);

            // every victim is caged in sliding amber rings with a beam from the eclipse
            for (LivingEntity e : victims) {
                if (!e.isValid()) continue;
                if (i % 10 == 0) Dmg.hit(e, p, dmg("totality-per-second", 3));
                Location f = e.getLocation();
                for (int k = 0; k < 3; k++) {
                    double h = ((i * 0.12) + k * 0.75) % 2.2;
                    Fx.ringDust(f.clone().add(0, h, 0), 0.8, 14, ECL_O, 1.1f);
                }
                Fx.ringDust(f.clone().add(0, 0.1, 0), 1.1, 14, ECL_B, 1.6f);
                if (i % 3 == 0) Fx.lineDust(sky, chest(e), 0.8, ECL_Y, 0.9f);
                w.spawnParticle(Particle.SQUID_INK, chest(e), Fx.n(2), 0.3, 0.5, 0.3, 0.01);
            }
        });

        Fx.later(plugin, 82, () -> {
            Fx.burst(sky, Particle.FIREWORK, 120, 2, 0.3);
            Fx.expandingRing(plugin, origin.clone().add(0, 0.15, 0), 1, 11, 8, ECL_Y, 1.8f, Particle.END_ROD);
            w.playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f);
        });
        return true;
    }

    // =====================================================================
    // ASTRAL
    // =====================================================================

    private boolean astralDash(Player p) {
        World w = p.getWorld();
        Location start = p.getLocation();

        Vector v = p.getLocation().getDirection().multiply(1.9);
        v.setY(Math.max(v.getY(), 0.35));
        p.setVelocity(v);
        p.setFallDistance(0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false));
        w.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.3f);
        w.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);

        // launch burst
        Fx.expandingRing(plugin, start.clone().add(0, 0.12, 0), 0.3, 3.2, 6, ASTRAL_C, 1.5f, Particle.END_ROD);
        Fx.burst(start.clone().add(0, 0.2, 0), Particle.CLOUD, 25, 0.5, 0.1);
        Fx.debris(start.clone().add(0, 0.3, 0), 25);
        Fx.burst(start.clone().add(0, 1, 0), Particle.END_ROD, 25, 0.4, 0.2);

        // glittering trail, hanging stars and rings
        Fx.run(plugin, 16, 1, i -> {
            if (!p.isOnline()) return;
            Location l = p.getLocation().add(0, 1, 0);
            w.spawnParticle(Particle.END_ROD, l, Fx.n(5), 0.25, 0.35, 0.25, 0.02);
            Fx.dustBurst(l, ASTRAL_C, 1.5f, 6, 0.3);
            Fx.dustBurst(l, WHITE, 1.0f, 4, 0.35);
            w.spawnParticle(Particle.ENCHANT, l, Fx.n(8), 0.5, 0.5, 0.5, 0.3);
            if (i % 2 == 0) star(l, 0.45, WHITE);
            if (i % 4 == 0) Fx.ringDust(l, 0.9, 16, ASTRAL_C, 1.2f);
        });
        return true;
    }

    private void star(Location c, double size, int rgb) {
        World w = c.getWorld();
        for (double d = -size; d <= size; d += size / 4) {
            Fx.dust(w, c.getX() + d, c.getY(), c.getZ(), rgb, 1.0f);
            Fx.dust(w, c.getX(), c.getY() + d, c.getZ(), rgb, 1.0f);
            Fx.dust(w, c.getX(), c.getY(), c.getZ() + d, rgb, 1.0f);
        }
    }

    private boolean starFall(Player p) {
        Location target = targetLocation(p, 25);
        World w = target.getWorld();
        w.playSound(target, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.5f);
        Fx.expandingRing(plugin, target.clone().add(0, 0.1, 0), 0.5, 4.5, 6, ASTRAL_C, 1.2f, null);
        Fx.burst(target.clone().add(0, 0.3, 0), Particle.ENCHANT, 80, 2, 0.4);

        Fx.loop(plugin, 5, i -> {
            if (i >= 8 || !p.isOnline()) return false;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Location impact = target.clone().add(rnd.nextDouble(-4, 4), 0, rnd.nextDouble(-4, 4));
            dropStar(p, impact);
            return true;
        });
        return true;
    }

    /** One comet: warning ring, glowing head with a tail, then a crater of light. */
    private void dropStar(Player p, Location impact) {
        World w = impact.getWorld();
        Fx.ringDust(impact.clone().add(0, 0.1, 0), 1.2, 14, WHITE, 1.0f);
        Vector offset = new Vector(5, 15, 3);
        Location start = impact.clone().add(offset);
        Vector delta = impact.toVector().subtract(start.toVector());

        Fx.run(plugin, 10, 1, i -> {
            double t = (i + 1) / 10.0;
            Location head = start.clone().add(delta.clone().multiply(t));
            for (int k = 0; k < 5; k++) {
                Location tail = head.clone().subtract(delta.clone().multiply(0.03 * k));
                w.spawnParticle(Particle.END_ROD, tail, 1, 0.05, 0.05, 0.05, 0);
                Fx.dust(tail, k % 2 == 0 ? ASTRAL_C : WHITE, 1.6f - k * 0.25f);
            }
            Fx.sphereDust(head, 0.3, 8, i, WHITE, 1.6f);

            if (i == 9) {
                w.spawnParticle(Particle.FIREWORK, impact, Fx.n(40), 0.4, 0.2, 0.4, 0.12);
                Fx.burst(impact.clone().add(0, 0.2, 0), Particle.END_ROD, 30, 0.3, 0.25);
                Fx.expandingRing(plugin, impact.clone().add(0, 0.1, 0), 0.3, 2.8, 5, ASTRAL_C, 1.4f, Particle.END_ROD);
                Fx.sphereDust(impact.clone().add(0, 0.6, 0), 0.9, 30, 0, WHITE, 1.5f);
                Fx.debris(impact.clone().add(0, 0.3, 0), 30);
                w.playSound(impact, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.6f);
                w.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.4f);
                for (Entity e : w.getNearbyEntities(impact, 2.5, 2.5, 2.5)) {
                    if (!isTarget(p, e)) continue;
                    LivingEntity le = (LivingEntity) e;
                    Dmg.hit(le, p, dmg("star-fall-per-star", 5));
                    le.setVelocity(le.getVelocity().add(new Vector(0, 0.4, 0)));
                }
            }
        });
    }

    // =====================================================================
    // CHAOS
    // =====================================================================

    private boolean chaosRoll(Player p) {
        PotionEffectType[] pool = {
                PotionEffectType.NAUSEA, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                PotionEffectType.POISON, PotionEffectType.BLINDNESS, PotionEffectType.LEVITATION,
                PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER
        };
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = p.getWorld();
        List<LivingEntity> victims = enemies(p, 9);
        if (nobodyAround(p, victims)) return false;

        for (LivingEntity e : victims) {
            PotionEffectType type = pool[rnd.nextInt(pool.length)];
            int duration = type == PotionEffectType.LEVITATION ? 40 : 100;
            int amplifier = type == PotionEffectType.SLOWNESS ? 1 : 0;
            e.addPotionEffect(new PotionEffect(type, duration, amplifier));
            Dmg.hit(e, p, dmg("chaos-roll", 4));
            Fx.burst(chest(e), Particle.TOTEM_OF_UNDYING, 30, 0.4, 0.3);
            Fx.burst(chest(e), Particle.ELECTRIC_SPARK, 25, 0.4, 0.15);
            Fx.burst(chest(e), Particle.WITCH, 20, 0.4, 0.05);
            curve(chest(p), chest(e), 1.0, CHAOS[rnd.nextInt(CHAOS.length)], 1.5f);
        }

        // rainbow spiral around the caster
        Fx.run(plugin, 16, 1, i -> {
            if (!p.isOnline()) return;
            Location b = p.getLocation();
            for (int arm = 0; arm < 3; arm++) {
                double a = i * 0.6 + arm * (2 * Math.PI / 3);
                double rad = 0.8 + i * 0.12;
                Fx.dust(w, b.getX() + Math.cos(a) * rad, b.getY() + 0.2 + i * 0.12, b.getZ() + Math.sin(a) * rad,
                        CHAOS[(i + arm) % CHAOS.length], 1.5f);
            }
        });

        // colorful orbs circling each victim
        Fx.run(plugin, 20, 1, i -> {
            for (LivingEntity e : victims) {
                if (!e.isValid()) continue;
                Location c = chest(e);
                for (int k = 0; k < 3; k++) {
                    double a = i * 0.4 + k * (2 * Math.PI / 3);
                    Fx.dust(w, c.getX() + Math.cos(a) * 0.9, c.getY() + Math.sin(i * 0.3 + k) * 0.5,
                            c.getZ() + Math.sin(a) * 0.9, CHAOS[(k + i / 5) % CHAOS.length], 1.4f);
                }
            }
        });
        Fx.burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.7, 0.3);
        w.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        w.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.8f);
        return true;
    }

    private boolean chaosStorm(Player p) {
        List<LivingEntity> targets = enemies(p, 14);
        if (nobodyAround(p, targets)) return false;
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.5f);
        Fx.expandingRing(plugin, p.getLocation().add(0, 0.1, 0), 0.5, 5, 8, CHAOS[0], 1.5f, Particle.ELECTRIC_SPARK);

        Fx.loop(plugin, 8, i -> {
            if (i >= 6 || !p.isOnline()) return false;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            LivingEntity t = targets.get(rnd.nextInt(targets.size()));
            if (t.isDead() || !t.isValid()) return true;

            Location spot = t.getLocation().add(rnd.nextDouble(-2, 2), 0, rnd.nextDouble(-2, 2));

            // telegraph: dark cloud above and a ring closing in on the spot
            Location cloud = spot.clone().add(0, 11, 0);
            w.spawnParticle(Particle.LARGE_SMOKE, cloud, Fx.n(25), 1.2, 0.3, 1.2, 0.01);
            Fx.dustBurst(cloud, 0x3B2A3A, 3f, 20, 1.2);
            w.spawnParticle(Particle.ELECTRIC_SPARK, cloud, Fx.n(15), 1.2, 0.3, 1.2, 0.1);
            Fx.expandingRing(plugin, spot.clone().add(0, 0.1, 0), 3.0, 0.5, 6, CHAOS[0], 1.2f, null);
            Fx.later(plugin, 6, () -> strike(p, spot));
            return true;
        });
        return true;
    }

    private void strike(Player p, Location spot) {
        World w = spot.getWorld();
        w.strikeLightningEffect(spot);
        Fx.column(spot, 12, 0.5, Particle.ELECTRIC_SPARK);
        Fx.debris(spot.clone().add(0, 0.3, 0), 40);
        Fx.helix(spot, 0.6, 8, 3, 30, 0, CHAOS[0], 1.4f);
        Fx.helix(spot, 0.6, 8, 3, 30, Math.PI, CHAOS[2], 1.4f);
        Fx.expandingRing(plugin, spot.clone().add(0, 0.1, 0), 0.5, 3.5, 5, 0xFDE047, 1.4f, Particle.ELECTRIC_SPARK);
        Fx.burst(spot.clone().add(0, 0.5, 0), Particle.ELECTRIC_SPARK, 40, 0.5, 0.3);
        Fx.burst(spot.clone().add(0, 0.5, 0), Particle.TOTEM_OF_UNDYING, 15, 0.4, 0.2);
        for (Entity e : w.getNearbyEntities(spot, 3, 3, 3)) {
            if (isTarget(p, e)) Dmg.hit((LivingEntity) e, p, dmg("chaos-storm-per-strike", 6));
        }
    }

    // =====================================================================
    // auras, awaken and level-up effects (called from elsewhere)
    // =====================================================================

    /** Signature particles so everyone can see what power a player has. Called every few ticks. */
    public void aura(Player p, Power power) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) return;
        if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) return;
        if (auraFancy) auraFancy(p, power);
        else auraSubtle(p, power);
    }

    /** The rich aura: a turning magic circle, three orbiting orbs and a different signature per power. */
    private void auraFancy(Player p, Power power) {
        World w = p.getWorld();
        Location b = p.getLocation();
        Location g = b.clone().add(0, 0.05, 0);
        Location mid = b.clone().add(0, 1, 0);
        int rgb = power.rgb();
        double t = (System.currentTimeMillis() % 100000) / 1000.0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // magic circle on the ground: a ring and a slowly turning triangle
        Fx.ringDust(g, 1.2, 18, rgb, 0.9f);
        for (int k = 0; k < 3; k++) {
            Fx.lineDust(onRing(g, t * 1.2 + k * TWO_PI / 3, 1.2), onRing(g, t * 1.2 + (k + 1) * TWO_PI / 3, 1.2),
                    0.45, rgb, 0.8f);
        }

        // three orbs circling at different heights
        for (int k = 0; k < 3; k++) {
            double a = t * 2.2 + k * 2.094;
            double r = 0.85 + 0.1 * Math.sin(t * 3 + k);
            Location orb = b.clone().add(Math.cos(a) * r, 0.6 + k * 0.5 + 0.1 * Math.sin(t * 4 + k), Math.sin(a) * r);
            Fx.sphereDust(orb, 0.12, 5, t, power == Power.CHAOS ? CHAOS[rnd.nextInt(CHAOS.length)] : rgb, 1.0f);
        }

        // sprinting leaves a trail
        if (p.isSprinting()) {
            w.spawnParticle(Particle.END_ROD, g, 2, 0.15, 0.05, 0.15, 0.01);
            Fx.dustBurst(g, rgb, 1.5f, 8, 0.25);
        }

        switch (power) {
            case VOID -> {
                w.spawnParticle(Particle.REVERSE_PORTAL, mid, 3, 0.45, 0.7, 0.45, 0.02);
                w.spawnParticle(Particle.PORTAL, g, 3, 0.8, 0.1, 0.8, 0.3);
            }
            case NULL -> {
                w.spawnParticle(Particle.ASH, mid, 3, 0.5, 0.7, 0.5, 0);
                Fx.ringDust(mid, 0.8 + 0.1 * Math.sin(t * 2), 14, NULL_W, 0.8f);
            }
            case LIGHT -> {
                Fx.ringDust(b.clone().add(0, 2.25, 0), 0.38, 10, LIGHT_Y, 1.0f);
                w.spawnParticle(Particle.END_ROD, mid, 2, 0.4, 0.8, 0.4, 0.01);
            }
            case DARK -> {
                w.spawnParticle(Particle.SQUID_INK, g, 2, 0.5, 0.05, 0.5, 0.01);
                w.spawnParticle(Particle.LARGE_SMOKE, g, 1, 0.4, 0.05, 0.4, 0.01);
                Fx.dustBurst(mid, DARK_B, 1.3f, 3, 0.45);
            }
            case SHADOW -> {
                w.spawnParticle(Particle.SMOKE, g, 3, 0.4, 0.05, 0.4, 0.01);
                Fx.dustBurst(g, SHADOW_D, 1.6f, 4, 0.4);
                w.spawnParticle(Particle.WITCH, mid, 1, 0.4, 0.7, 0.4, 0.01);
            }
            case ECLIPSE -> {
                Location sky = b.clone().add(0, 2.7, 0);
                Fx.sphereDust(sky, 0.2, 8, t, ECL_B, 1.4f);
                Fx.ringDust(sky, 0.34, 10, ECL_O, 1.0f);
                Fx.sphereDust(sky, 0.3, 6, -t, ECL_Y, 0.7f);
            }
            case ASTRAL -> {
                star(mid.clone().add(Math.cos(t * 2) * 0.9, 0.4, Math.sin(t * 2) * 0.9), 0.14, WHITE);
                w.spawnParticle(Particle.END_ROD, mid, 2, 0.4, 0.8, 0.4, 0.015);
            }
            case CHAOS -> {
                w.spawnParticle(Particle.ELECTRIC_SPARK, mid, 3, 0.5, 0.8, 0.5, 0.05);
                Fx.dustBurst(mid, CHAOS[rnd.nextInt(CHAOS.length)], 1.2f, 3, 0.5);
            }
        }
    }

    private Location onRing(Location center, double angle, double radius) {
        return new Location(center.getWorld(), center.getX() + Math.cos(angle) * radius,
                center.getY(), center.getZ() + Math.sin(angle) * radius);
    }

    /** The old, quiet aura (aura-style: subtle). */
    private void auraSubtle(Player p, Power power) {
        World w = p.getWorld();
        Location b = p.getLocation();
        double a = (System.currentTimeMillis() % 6283) / 1000.0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location mid = b.clone().add(0, 1, 0);

        switch (power) {
            case VOID -> {
                orbit(w, b, a, 0.9, 1.0, VOID, 1.1f);
                w.spawnParticle(Particle.REVERSE_PORTAL, mid, 2, 0.4, 0.6, 0.4, 0.02);
            }
            case NULL -> {
                orbit(w, b, a, 0.9, 1.0, NULL_W, 1.0f);
                w.spawnParticle(Particle.ASH, mid, 2, 0.4, 0.6, 0.4, 0);
            }
            case LIGHT -> {
                orbit(w, b, a, 0.9, 1.0, LIGHT_Y, 1.1f);
                w.spawnParticle(Particle.END_ROD, mid, 1, 0.4, 0.7, 0.4, 0.005);
            }
            case DARK -> {
                orbit(w, b, a, 0.9, 1.0, DARK_P, 1.1f);
                Fx.dustBurst(mid, DARK_B, 1.3f, 3, 0.4);
                w.spawnParticle(Particle.SMOKE, mid, 1, 0.3, 0.6, 0.3, 0.005);
            }
            case SHADOW -> {
                Fx.dustBurst(b.clone().add(0, 0.2, 0), SHADOW_D, 1.5f, 3, 0.35);
                w.spawnParticle(Particle.SMOKE, b.clone().add(0, 0.2, 0), 1, 0.3, 0.1, 0.3, 0.005);
                orbit(w, b, a, 0.7, 0.6, SHADOW_P, 1.0f);
            }
            case ECLIPSE -> {
                Location halo = b.clone().add(0, 2.35, 0);
                Fx.ringDust(halo, 0.35, 8, ECL_O, 1.0f);
                Fx.dust(halo, ECL_B, 1.4f);
                orbit(w, b, a, 0.9, 1.0, ECL_Y, 1.0f);
            }
            case ASTRAL -> {
                orbit(w, b, a, 0.9, 1.0, ASTRAL_C, 1.1f);
                w.spawnParticle(Particle.END_ROD, mid, 1, 0.4, 0.7, 0.4, 0.01);
            }
            case CHAOS -> {
                orbit(w, b, a, 0.9, 1.0, CHAOS[rnd.nextInt(CHAOS.length)], 1.2f);
                w.spawnParticle(Particle.ELECTRIC_SPARK, mid, 1, 0.4, 0.7, 0.4, 0.05);
            }
        }
    }

    private void orbit(World w, Location base, double angle, double radius, double height, int rgb, float size) {
        for (int k = 0; k < 2; k++) {
            double a = angle * 2 + k * Math.PI;
            Fx.dust(w, base.getX() + Math.cos(a) * radius, base.getY() + height + Math.sin(angle * 3) * 0.3,
                    base.getZ() + Math.sin(a) * radius, rgb, size);
        }
    }

    /** Big reveal when a player receives a power. */
    public void awakenEffect(Player p, Power power) {
        int rgb = power.rgb();
        World w = p.getWorld();
        Location b = p.getLocation();

        Fx.expandingRing(plugin, b.clone().add(0, 0.1, 0), 0.5, 6, 12, rgb, 1.6f, Particle.END_ROD);
        Fx.later(plugin, 4, () -> Fx.expandingRing(plugin, p.getLocation().add(0, 0.1, 0), 0.5, 8, 14, WHITE, 1.3f, null));
        Fx.run(plugin, 24, 1, i -> {
            if (!p.isOnline()) return;
            Fx.helix(p.getLocation(), 1.2 - i * 0.03, 3, 2, 30, i * 0.5, rgb, 1.4f);
        });
        Fx.run(plugin, 8, 1, i ->
                Fx.sphereDust(p.getLocation().add(0, 1, 0), 0.8 + i * 0.5, 50, i * 0.4, rgb, 1.5f));
        Fx.column(b, 10, 0.4, Particle.END_ROD);
        Fx.burst(b.clone().add(0, 1, 0), Particle.FIREWORK, 80, 0.8, 0.2);
        Fx.burst(b.clone().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.6, 0.3);
        w.playSound(b, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
        w.playSound(b, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
    }

    /** Even bigger when ability 2 unlocks. */
    public void levelUpEffect(Player p, Power power) {
        int rgb = power.rgb();
        World w = p.getWorld();
        Location b = p.getLocation();

        Fx.expandingRing(plugin, b.clone().add(0, 0.1, 0), 0.5, 8, 12, rgb, 1.8f, Particle.END_ROD);
        Fx.expandingRing(plugin, b.clone().add(0, 1.0, 0), 0.5, 8, 14, WHITE, 1.4f, null);
        Fx.expandingRing(plugin, b.clone().add(0, 2.0, 0), 0.5, 8, 16, rgb, 1.4f, null);
        Fx.run(plugin, 30, 1, i -> {
            if (!p.isOnline()) return;
            Location l = p.getLocation();
            Fx.helix(l, 1.0, 3.2, 2, 28, i * 0.45, rgb, 1.5f);
            Fx.helix(l, 1.0, 3.2, 2, 28, i * 0.45 + Math.PI, WHITE, 1.2f);
        });
        Fx.column(b, 14, 0.4, Particle.END_ROD);
        Fx.burst(b.clone().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 80, 0.7, 0.4);
        Fx.burst(b.clone().add(0, 1, 0), Particle.FIREWORK, 100, 1.0, 0.25);
        w.playSound(b, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        w.playSound(b, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private boolean isTarget(Player caster, Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e.equals(caster) || e instanceof ArmorStand) return false;
        if (e instanceof Player other) return other.getGameMode() != GameMode.SPECTATOR;
        // Mobs: villagers, pets and farm animals stay safe unless target-mode is "all".
        return switch (targetMode) {
            case "players" -> false;
            case "all" -> true;
            default -> e instanceof Enemy;
        };
    }

    /** For abilities that only affect enemies: tells the caster and returns true when nobody is in range. */
    private boolean nobodyAround(Player p, List<LivingEntity> list) {
        if (!list.isEmpty()) return false;
        p.sendActionBar(Component.text("No enemies in range", NamedTextColor.GRAY));
        return true;
    }

    private List<LivingEntity> enemies(Player p, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity le && isTarget(p, le)
                    && le.getLocation().distanceSquared(p.getLocation()) <= r2) {
                out.add(le);
            }
        }
        return out;
    }

    private Location chest(LivingEntity e) {
        return e.getLocation().add(0, e.getHeight() * 0.6, 0);
    }

    private Vector rightOf(Entity e) {
        Vector d = e.getLocation().getDirection().setY(0);
        return new Vector(-d.getZ(), 0, d.getX());
    }

    private Vector randomUnit() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double z = r.nextDouble(-1, 1);
        double a = r.nextDouble(0, 2 * Math.PI);
        double s = Math.sqrt(1 - z * z);
        return new Vector(s * Math.cos(a), z, s * Math.sin(a));
    }

    /** A curved line of dust between two points. lift bends it up (or down if negative). */
    private void curve(Location a, Location b, double lift, int rgb, float size) {
        Location ctrl = new Location(a.getWorld(), (a.getX() + b.getX()) / 2,
                (a.getY() + b.getY()) / 2 + lift, (a.getZ() + b.getZ()) / 2);
        for (double t = 0; t <= 1; t += 0.06) {
            Fx.dust(Fx.bezier(a, ctrl, b, t), rgb, size);
        }
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
    
