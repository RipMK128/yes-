package dev.smp.eclipsepowers;

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

/** Every ability returns true if it was used and false if it could not be used. */
public final class Abilities {

    private static final int VOID = 0x8A2BE2;
    private static final int VOID_LT = 0xD9B8FF;
    private static final int NULL_W = 0xE5E7EB;
    private static final int NULL_G = 0x9AA0A6;
    private static final int LIGHT_Y = 0xFFE066;
    private static final int LIGHT_W = 0xFFF8D6;
    private static final int DARK_P = 0x4C3F8F;
    private static final int SHADOW_P = 0x5B4D73;
    private static final int ECL_O = 0xFF9F1C;
    private static final int ECL_Y = 0xFFCF70;
    private static final int ECL_B = 0x050505;
    private static final int ASTRAL_C = 0x4CC9F0;
    private static final int WHITE = 0xFFFFFF;

    private final EclipsePowers plugin;
    private final PowerManager manager;
    private final Map<UUID, Long> lastRipple = new HashMap<>();
    private String targetMode = "hostile";

    public Abilities(EclipsePowers plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        reloadSettings();
    }

    public void reloadSettings() {
        targetMode = plugin.getConfig().getString("target-mode", "hostile").toLowerCase(Locale.ROOT);
    }

    private double dmg(String key, double fallback) {
        return Math.max(0, plugin.getConfig().getDouble("damage." + key, fallback));
    }

    public boolean cast(Player p, Power power, int slot) {
        if (power.ordinal() >= Power.NATURE.ordinal()) {
            boolean used = NewPowers.cast(plugin, manager, p, power, slot);
            if (used) CustomEffects.cast(p, power, slot);
            return used;
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
            default -> false;
        };
        if (ok) flourish(p, power, slot);
        return ok;
    }

    private void flourish(Player p, Power power, int slot) {
        Location b = p.getLocation();
        int rgb = power.rgb();
        double big = slot == 2 ? 1.0 : 0.0;
        Fx.expandingRing(plugin, b.clone().add(0, 0.1, 0), 0.3, 2.0 + big * 1.4, 5, rgb, 1.4f, null);
        Fx.dustBurst(b.clone().add(0, 1, 0), rgb, 1.4f, 16, 0.45);
        CustomEffects.cast(p, power, slot);
        p.getWorld().playSound(b, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, slot == 2 ? 1.6f : 1.9f);
    }

    public void killEffect(Player killer, Location where, Power power) {
        Location c = where.clone().add(0, 1, 0);
        int rgb = power.rgb();
        Fx.burst(c, Particle.SOUL, 30, 0.4, 0.1);
        Fx.dustBurst(c, rgb, 1.8f, 40, 0.7);
        Fx.expandingRing(plugin, where.clone().add(0, 0.1, 0), 0.3, 3.5, 7, rgb, 1.5f, Particle.END_ROD);
        CustomEffects.cast(killer, power, 1);
        where.getWorld().playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    private boolean voidPull(Player p) {
        List<LivingEntity> targets = enemies(p, 9);
        if (nobodyAround(p, targets)) return false;
        Location base = p.getLocation();
        for (LivingEntity target : targets) {
            Dmg.hit(target, p, dmg("void-pull", 3));
            Vector pull = base.toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.2);
            pull.setY(target.getVelocity().getY() + 0.3);
            target.setVelocity(pull);
        }
        Fx.expandingRing(plugin, base.clone().add(0, 0.1, 0), 0.5, 9, 9, VOID, 1.5f, Particle.PORTAL);
        return true;
    }

    private boolean voidRift(Player p) { return false; }
    private boolean nullPulse(Player p) { return false; }
    private boolean nullShield(Player p) { manager.shield(p, 4000); return true; }
    private boolean lightBeam(Player p) { return false; }
    private boolean solarFlare(Player p) { return false; }
    private boolean darkVeil(Player p) { return false; }
    private boolean soulDrain(Player p) { return false; }
    private boolean shadowStep(Player p) { return false; }
    private boolean shadowStrike(Player p) { return false; }
    private boolean eclipseFlash(Player p) { return false; }
    private boolean totality(Player p) { return false; }
    private boolean astralDash(Player p) { return false; }
    private boolean starFall(Player p) { return false; }
    private boolean chaosRoll(Player p) { return false; }
    private boolean chaosStorm(Player p) { return false; }

    public void shieldRipple(Player p) {
        Long last = lastRipple.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 200) return;
        lastRipple.put(p.getUniqueId(), now);
        Fx.burst(p.getLocation().add(0, 1, 0), Particle.END_ROD, 8, 0.15, 0.04);
    }

    public void aura(Player p, Power power) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || p.isInvisible()) return;
        CustomEffects.aura(p, power);
    }

    public void awakenEffect(Player p, Power power) {
        Location b = p.getLocation();
        Fx.expandingRing(plugin, b.clone().add(0, 0.1, 0), 0.5, 6, 12, power.rgb(), 1.6f, Particle.END_ROD);
        Fx.burst(b.clone().add(0, 1, 0), Particle.FIREWORK, 40, 0.8, 0.2);
        CustomEffects.cast(p, power, 2);
    }

    public void levelUpEffect(Player p, Power power) {
        Location b = p.getLocation();
        Fx.expandingRing(plugin, b.clone().add(0, 0.1, 0), 0.5, 8, 12, power.rgb(), 1.8f, Particle.END_ROD);
        Fx.burst(b.clone().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.7, 0.4);
        CustomEffects.cast(p, power, 2);
    }

    private boolean isTarget(Player caster, Entity e) {
        if (!(e instanceof LivingEntity) || e.equals(caster) || e instanceof ArmorStand) return false;
        if (e instanceof Player other) return other.getGameMode() != GameMode.SPECTATOR;
        return switch (targetMode) {
            case "players" -> false;
            case "all" -> true;
            default -> e instanceof Enemy;
        };
    }

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

    private Location targetLocation(Player p, double range) {
        RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(p.getWorld());
        }
        return p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
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
}
