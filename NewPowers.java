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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Abilities for Nature, Storm, Frost, Earth, and Time. */
public final class NewPowers {
    private static final int GREEN = 0x54E36B;
    private static final int PALE_GREEN = 0xB7F7C2;
    private static final int STORM_BLUE = 0x7DEBFF;
    private static final int ICE_BLUE = 0x8DEBFF;
    private static final int EARTH_BROWN = 0xC98A52;
    private static final int TIME_PURPLE = 0xA98BFF;
    private static final int WHITE = 0xFFFFFF;

    private NewPowers() {
    }

    public static boolean cast(EclipsePowers plugin, PowerManager manager,
                               Player p, Power power, int slot) {
        boolean used = switch (power) {
            case NATURE -> slot == 1 ? seedBurst(plugin, p) : wildgrowth(plugin, p);
            case STORM -> slot == 1 ? chainLightning(plugin, p) : thunderstorm(plugin, p);
            case FROST -> slot == 1 ? frostNova(plugin, p) : iceLance(plugin, p);
            case EARTH -> slot == 1 ? seismicSlam(plugin, p) : stoneguard(plugin, p);
            case TIME -> slot == 1 ? quickstep(plugin, p) : timeSnare(plugin, p);
            default -> false;
        };
        if (used) flourish(plugin, p, power, slot);
        return used;
    }

    private static boolean seedBurst(EclipsePowers plugin, Player p) {
        List<LivingEntity> targets = enemies(plugin, p, 8);
        if (targets.isEmpty()) return noTargets(p);
        Location center = p.getLocation();
        World world = p.getWorld();
        Fx.expandingRing(plugin, center.clone().add(0, 0.15, 0), 0.4, 8, 9,
                GREEN, 1.6f, Particle.HAPPY_VILLAGER);
        Fx.spokes(center.clone().add(0, 0.12, 0), 7, 12, PALE_GREEN, 1.0f);
        Fx.burst(center.clone().add(0, 1, 0), Particle.COMPOSTER, 70, 1.2, 0.25);
        Fx.burst(center.clone().add(0, 1, 0), Particle.HAPPY_VILLAGER, 35, 1.0, 0.15);
        for (LivingEntity target : targets) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
            Dmg.hit(target, p, damage(plugin, "seed-burst", 4));
            Location at = chest(target);
            Fx.sphereDust(at, 0.8, 28, 0, GREEN, 1.3f);
            Fx.burst(at, Particle.COMPOSTER, 24, 0.35, 0.12);
            Fx.lineDust(chest(p), at, 0.45, PALE_GREEN, 1.0f);
        }
        world.playSound(center, Sound.ITEM_BONE_MEAL_USE, 1f, 0.8f);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
        return true;
    }

    private static boolean wildgrowth(EclipsePowers plugin, Player p) {
        World world = p.getWorld();
        Location start = p.getLocation();
        heal(p, 8);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 0));
        Fx.expandingRing(plugin, start.clone().add(0, 0.1, 0), 0.3, 5, 10,
                GREEN, 1.6f, Particle.HAPPY_VILLAGER);
        Fx.burst(start.clone().add(0, 1, 0), Particle.COMPOSTER, 55, 0.8, 0.2);
        Fx.spiralUp(plugin, p, 36, 1.25, GREEN, 1.5f, Particle.HAPPY_VILLAGER);
        Fx.run(plugin, 12, 1, i -> {
            if (!p.isOnline()) return;
            Location at = p.getLocation().add(0, 2.2 + i * 0.12, 0);
            Fx.sphereDust(at, 0.35 + i * 0.07, 24, i * 0.3, PALE_GREEN, 1.2f);
            world.spawnParticle(Particle.COMPOSTER, at, Fx.n(5), 0.5, 0.3, 0.5, 0.08);
        });
        world.playSound(start, Sound.ITEM_BONE_MEAL_USE, 1f, 1.2f);
        world.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
        return true;
    }

    private static boolean chainLightning(EclipsePowers plugin, Player p) {
        List<LivingEntity> targets = enemies(plugin, p, 12);
        if (targets.isEmpty()) return noTargets(p);
        targets.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
        Location origin = chest(p);
        Fx.expandingRing(plugin, p.getLocation().add(0, 0.1, 0), 0.4, 5, 7,
                STORM_BLUE, 1.4f, Particle.ELECTRIC_SPARK);
        Location previous = origin;
        int hits = Math.min(5, targets.size());
        for (int i = 0; i < hits; i++) {
            LivingEntity target = targets.get(i);
            Location at = chest(target);
            lightningArc(previous, at, i);
            Dmg.hit(target, p, damage(plugin, "chain-lightning", 5));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            Fx.burst(at, Particle.ELECTRIC_SPARK, 24, 0.3, 0.12);
            previous = at;
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.7f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
        return true;
    }

    private static boolean thunderstorm(EclipsePowers plugin, Player p) {
        List<LivingEntity> targets = enemies(plugin, p, 14);
        if (targets.isEmpty()) return noTargets(p);
        World world = p.getWorld();
        Location origin = p.getLocation();
        Fx.expandingRing(plugin, origin.clone().add(0, 0.1, 0), 0.5, 8, 10,
                STORM_BLUE, 1.5f, Particle.ELECTRIC_SPARK);
        Fx.burst(origin.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 35, 0.7, 0.1);
        world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.8f);
        Fx.loop(plugin, 8, i -> {
            if (i >= 6 || !p.isOnline()) return false;
            List<LivingEntity> valid = targets.stream().filter(e -> e.isValid() && !e.isDead()).toList();
            if (valid.isEmpty()) return false;
            LivingEntity target = valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
            Location strike = target.getLocation();
            Fx.ringDust(strike.clone().add(0, 0.1, 0), 2.4, 28, STORM_BLUE, 1.0f);
            Location cloud = strike.clone().add(0, 8, 0);
            Fx.sphereDust(cloud, 1.1, 28, i, STORM_BLUE, 1.0f);
            Fx.later(plugin, 5, () -> {
                if (!target.isValid() || target.isDead()) return;
                Location impact = target.getLocation();
                world.strikeLightningEffect(impact);
                Fx.column(impact, 7, 0.35, Particle.ELECTRIC_SPARK);
                Fx.burst(chest(target), Particle.ELECTRIC_SPARK, 30, 0.4, 0.18);
                Dmg.hit(target, p, damage(plugin, "thunderstorm-per-strike", 4));
                world.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.2f);
            });
            return true;
        });
        return true;
    }

    private static boolean frostNova(EclipsePowers plugin, Player p) {
        List<LivingEntity> targets = enemies(plugin, p, 8);
        if (targets.isEmpty()) return noTargets(p);
        Location center = p.getLocation();
        Fx.expandingRing(plugin, center.clone().add(0, 0.15, 0), 0.4, 8, 9,
                ICE_BLUE, 1.7f, Particle.SNOWFLAKE);
        Fx.expandingRing(plugin, center.clone().add(0, 1, 0), 0.3, 7, 10,
                WHITE, 1.1f, null);
        Fx.burst(center.clone().add(0, 1, 0), Particle.SNOWFLAKE, 90, 1.4, 0.08);
        Fx.sphereDust(center.clone().add(0, 1, 0), 1.0, 45, 0, ICE_BLUE, 1.5f);
        for (LivingEntity target : targets) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), 50));
            Dmg.hit(target, p, damage(plugin, "frost-nova", 3));
            Fx.burst(chest(target), Particle.SNOWFLAKE, 28, 0.4, 0.04);
            Fx.sphereDust(chest(target), 0.65, 24, 0, WHITE, 1.2f);
        }
        p.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        p.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
        return true;
    }

    private static boolean iceLance(EclipsePowers plugin, Player p) {
        Location eye = p.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult hit = p.getWorld().rayTrace(eye, direction, 24, FluidCollisionMode.NEVER,
                true, 0.45, entity -> isTarget(plugin, p, entity));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            p.sendActionBar("No target in range");
            return false;
        }
        Location end = hit.getHitPosition().toLocation(p.getWorld());
        Fx.lineDust(eye, end, 0.18, ICE_BLUE, 1.6f);
        Fx.line(eye, end, 0.25, Particle.SNOWFLAKE);
        Fx.burst(eye.clone().add(direction.clone().multiply(0.8)), Particle.SNOWFLAKE, 35, 0.2, 0.05);
        Fx.sphereDust(end, 0.6, 30, 0, WHITE, 1.5f);
        Fx.burst(end, Particle.SNOWFLAKE, 35, 0.3, 0.08);
        Dmg.hit(target, p, damage(plugin, "ice-lance", 8));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), 80));
        p.getWorld().playSound(eye, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.8f);
        p.getWorld().playSound(end, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
        return true;
    }

    private static boolean seismicSlam(EclipsePowers plugin, Player p) {
        List<LivingEntity> targets = enemies(plugin, p, 8);
        if (targets.isEmpty()) return noTargets(p);
        Location center = p.getLocation();
        World world = p.getWorld();
        Fx.expandingRing(plugin, center.clone().add(0, 0.1, 0), 0.5, 8, 8,
                EARTH_BROWN, 1.7f, Particle.BLOCK);
        Fx.spokes(center.clone().add(0, 0.12, 0), 7, 14, EARTH_BROWN, 1.3f);
        Fx.debris(center.clone().add(0, 0.3, 0), 60);
        Fx.burst(center.clone().add(0, 0.4, 0), Particle.BLOCK, 45, 1.0, 0.2);
        for (LivingEntity target : targets) {
            Dmg.hit(target, p, damage(plugin, "seismic-slam", 6));
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(0, 0, 1);
            push.normalize().multiply(1.0).setY(0.65);
            target.setVelocity(push);
            Fx.burst(chest(target), Particle.BLOCK, 24, 0.4, 0.15);
        }
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.3f);
        return true;
    }

    private static boolean stoneguard(EclipsePowers plugin, Player p) {
        Location start = p.getLocation();
        World world = p.getWorld();
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 2));
        Fx.expandingRing(plugin, start.clone().add(0, 0.1, 0), 0.4, 4, 8,
                EARTH_BROWN, 1.6f, Particle.BLOCK);
        Fx.burst(start.clone().add(0, 1, 0), Particle.BLOCK, 55, 0.7, 0.18);
        Fx.run(plugin, 40, 2, i -> {
            if (!p.isOnline()) return;
            Location c = p.getLocation().add(0, 1, 0);
            double angle = i * 0.28;
            for (int k = 0; k < 4; k++) {
                double a = angle + k * Math.PI / 2;
                Fx.dust(world, c.getX() + Math.cos(a) * 1.15, c.getY() + 0.7 * Math.sin(i * 0.2 + k),
                        c.getZ() + Math.sin(a) * 1.15, EARTH_BROWN, 1.5f);
            }
            Fx.ringDust(c, 1.15, 20, EARTH_BROWN, 1.0f);
            if (i % 5 == 0) Fx.burst(c, Particle.BLOCK, 6, 0.6, 0.05);
        });
        world.playSound(start, Sound.BLOCK_ANVIL_USE, 0.9f, 0.8f);
        world.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.7f);
        return true;
    }

    private static boolean quickstep(EclipsePowers plugin, Player p) {
        Location start = p.getLocation();
        World world = p.getWorld();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 140, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 140, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 140, 0));
        Vector velocity = p.getLocation().getDirection().multiply(0.8);
        velocity.setY(Math.max(velocity.getY(), 0.25));
        p.setVelocity(velocity);
        p.setFallDistance(0);
        Fx.expandingRing(plugin, start.clone().add(0, 0.1, 0), 0.3, 3.5, 6,
                TIME_PURPLE, 1.5f, Particle.END_ROD);
        Fx.burst(start.clone().add(0, 1, 0), Particle.END_ROD, 35, 0.4, 0.15);
        Fx.burst(start.clone().add(0, 0.2, 0), Particle.CLOUD, 25, 0.5, 0.1);
        Fx.run(plugin, 24, 1, i -> {
            if (!p.isOnline()) return;
            Location at = p.getLocation().add(0, 1, 0);
            Fx.dustBurst(at, TIME_PURPLE, 1.4f, 5, 0.3);
            world.spawnParticle(Particle.END_ROD, at, Fx.n(3), 0.25, 0.35, 0.25, 0.02);
            if (i % 4 == 0) Fx.ringDust(at, 0.65, 14, WHITE, 0.9f);
        });
        world.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
        world.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
        return true;
    }

    private static boolean timeSnare(EclipsePowers plugin, Player p) {
        List<LivingEntity> targets = enemies(plugin, p, 10);
        if (targets.isEmpty()) return noTargets(p);
        Location center = p.getLocation();
        Fx.expandingRing(plugin, center.clone().add(0, 0.1, 0), 0.5, 10, 10,
                TIME_PURPLE, 1.6f, Particle.END_ROD);
        Fx.expandingRing(plugin, center.clone().add(0, 1, 0), 0.5, 8, 12,
                WHITE, 1.0f, null);
        for (LivingEntity target : targets) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0));
            target.setVelocity(target.getVelocity().multiply(0.25));
            Dmg.hit(target, p, damage(plugin, "time-snare", 3));
            Location at = chest(target);
            Fx.ringDust(at, 0.8, 18, TIME_PURPLE, 1.2f);
            Fx.sphereDust(at, 0.9, 28, 0, WHITE, 0.9f);
            Fx.lineDust(center.clone().add(0, 1, 0), at, 0.45, TIME_PURPLE, 1.0f);
        }
        p.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.5f);
        p.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
        return true;
    }

    private static void lightningArc(Location from, Location to, int index) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(2, (int) (delta.length() * 5));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location previous = from;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = from.clone().add(delta.clone().multiply(t));
            if (i < steps) point.add(random.nextDouble(-0.2, 0.2), random.nextDouble(-0.2, 0.2), random.nextDouble(-0.2, 0.2));
            Fx.lineDust(previous, point, 0.12, index % 2 == 0 ? STORM_BLUE : WHITE, 1.2f);
            point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0.02);
            previous = point;
        }
    }

    private static List<LivingEntity> enemies(EclipsePowers plugin, Player caster, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && isTarget(plugin, caster, entity)
                    && living.getLocation().distanceSquared(caster.getLocation()) <= radiusSquared) {
                found.add(living);
            }
        }
        return found;
    }

    private static boolean isTarget(EclipsePowers plugin, Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity) || entity.equals(caster) || entity instanceof ArmorStand) return false;
        if (entity instanceof Player other) return other.getGameMode() != GameMode.SPECTATOR;
        String mode = plugin.getConfig().getString("target-mode", "hostile").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "players" -> false;
            case "all" -> true;
            default -> entity instanceof Enemy;
        };
    }

    private static boolean noTargets(Player p) {
        p.sendActionBar("No enemies in range");
        return false;
    }

    private static double damage(EclipsePowers plugin, String key, double fallback) {
        return Math.max(0, plugin.getConfig().getDouble("damage." + key, fallback));
    }

    private static Location chest(LivingEntity entity) {
        return entity.getLocation().add(0, entity.getHeight() * 0.6, 0);
    }

    private static void heal(Player p, double amount) {
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealth == null ? 20.0 : maxHealth.getValue();
        p.setHealth(Math.min(cap, p.getHealth() + amount));
    }

    private static void flourish(EclipsePowers plugin, Player p, Power power, int slot) {
        Location base = p.getLocation();
        int rgb = power.rgb();
        Fx.expandingRing(plugin, base.clone().add(0, 0.1, 0), 0.3, slot == 2 ? 3.4 : 2.2,
                6, rgb, 1.4f, Particle.END_ROD);
        Fx.burst(base.clone().add(0, 1, 0), Particle.END_ROD, slot == 2 ? 28 : 16, 0.35, 0.08);
        Fx.dustBurst(base.clone().add(0, 1, 0), rgb, 1.3f, 18, 0.4);
        p.getWorld().playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 0.55f, slot == 2 ? 1.6f : 1.9f);
    }
}
