package dev.smp.eclipsepowers;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.logging.Level;

/** Reusable particle shapes and small animation helpers used by the abilities. */
public final class Fx {

    private static double density = 1.0;

    private Fx() {
    }

    /** 1.0 = normal. Lower it on busy servers, raise it for more spectacle. */
    public static void setDensity(double value) {
        density = Math.max(0.2, Math.min(3.0, value));
    }

    public static int n(int count) {
        return Math.max(1, (int) Math.round(count * density));
    }

    // ------------------------------------------------------------------
    // single particles and bursts
    // ------------------------------------------------------------------

    public static void dust(World w, double x, double y, double z, int rgb, float size) {
        w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.fromRGB(rgb), size));
    }

    public static void dust(Location l, int rgb, float size) {
        dust(l.getWorld(), l.getX(), l.getY(), l.getZ(), rgb, size);
    }

    public static void dustBurst(Location c, int rgb, float size, int count, double spread) {
        c.getWorld().spawnParticle(Particle.DUST, c, n(count), spread, spread, spread, 0,
                new Particle.DustOptions(Color.fromRGB(rgb), size));
    }

    public static void burst(Location c, Particle particle, int count, double spread, double speed) {
        c.getWorld().spawnParticle(particle, c, n(count), spread, spread, spread, speed);
    }

    // ------------------------------------------------------------------
    // shapes
    // ------------------------------------------------------------------

    public static void ring(Location c, double r, int points, Particle particle) {
        World w = c.getWorld();
        int pts = n(points);
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0);
        }
    }

    public static void ringDust(Location c, double r, int points, int rgb, float size) {
        World w = c.getWorld();
        int pts = n(points);
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            dust(w, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r, rgb, size);
        }
    }

    /** Hollow sphere of dust. spin rotates the point pattern so it looks alive when redrawn each tick. */
    public static void sphereDust(Location c, double r, int points, double spin, int rgb, float size) {
        World w = c.getWorld();
        int pts = n(points);
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < pts; i++) {
            double y = 1 - (2.0 * i + 1) / pts;
            double ring = Math.sqrt(Math.max(0, 1 - y * y));
            double theta = golden * i + spin;
            dust(w, c.getX() + Math.cos(theta) * ring * r, c.getY() + y * r,
                    c.getZ() + Math.sin(theta) * ring * r, rgb, size);
        }
    }

    public static void lineDust(Location a, Location b, double step, int rgb, float size) {
        Vector delta = b.toVector().subtract(a.toVector());
        double len = delta.length();
        if (len < 0.01) return;
        Vector dir = delta.normalize();
        World w = a.getWorld();
        double s = Math.max(0.1, step / density);
        for (double d = 0; d <= len; d += s) {
            dust(w, a.getX() + dir.getX() * d, a.getY() + dir.getY() * d, a.getZ() + dir.getZ() * d, rgb, size);
        }
    }

    public static void line(Location a, Location b, double step, Particle particle) {
        Vector delta = b.toVector().subtract(a.toVector());
        double len = delta.length();
        if (len < 0.01) return;
        Vector dir = delta.normalize();
        World w = a.getWorld();
        double s = Math.max(0.1, step / density);
        for (double d = 0; d <= len; d += s) {
            w.spawnParticle(particle, a.getX() + dir.getX() * d, a.getY() + dir.getY() * d,
                    a.getZ() + dir.getZ() * d, 1, 0, 0, 0, 0);
        }
    }

    public static void column(Location base, double height, double step, Particle particle) {
        World w = base.getWorld();
        double s = Math.max(0.1, step / density);
        for (double h = 0; h <= height; h += s) {
            w.spawnParticle(particle, base.getX(), base.getY() + h, base.getZ(), 1, 0, 0, 0, 0);
        }
    }

    public static void helix(Location base, double radius, double height, double turns, int points,
                             double phase, int rgb, float size) {
        World w = base.getWorld();
        int pts = n(points);
        for (int i = 0; i < pts; i++) {
            double t = i / (double) pts;
            double a = phase + t * turns * 2 * Math.PI;
            dust(w, base.getX() + Math.cos(a) * radius, base.getY() + t * height,
                    base.getZ() + Math.sin(a) * radius, rgb, size);
        }
    }

    /** Chips of the block under `ground` flying up, like an impact in the terrain. */
    public static void debris(Location ground, int count) {
        Block below = ground.clone().subtract(0, 0.6, 0).getBlock();
        if (below.getType().isAir()) return;
        ground.getWorld().spawnParticle(Particle.BLOCK, ground, n(count), 0.5, 0.15, 0.5, 0.08, below.getBlockData());
    }

    /** Straight lines fanning out from a point along the ground, like cracks. */
    public static void spokes(Location center, double length, int count, int rgb, float size) {
        for (int k = 0; k < count; k++) {
            double a = 2 * Math.PI * k / count;
            Location end = new Location(center.getWorld(), center.getX() + Math.cos(a) * length,
                    center.getY(), center.getZ() + Math.sin(a) * length);
            lineDust(center, end, 0.6, rgb, size);
        }
    }

    /** Point on a quadratic bezier curve, t from 0 to 1. */
    public static Location bezier(Location a, Location control, Location b, double t) {
        double u = 1 - t;
        return new Location(a.getWorld(),
                u * u * a.getX() + 2 * u * t * control.getX() + t * t * b.getX(),
                u * u * a.getY() + 2 * u * t * control.getY() + t * t * b.getY(),
                u * u * a.getZ() + 2 * u * t * control.getZ() + t * t * b.getZ());
    }

    /** A flat, person-shaped outline of dust. right = the direction across the figure's shoulders. */
    public static void silhouette(Location feet, Vector right, int rgb, float size) {
        World w = feet.getWorld();
        Vector r = right.clone().setY(0);
        if (r.lengthSquared() < 0.0001) r = new Vector(1, 0, 0);
        r.normalize();
        for (double y = 0; y <= 0.7; y += 0.23) {
            point(w, feet, r, -0.12, y, rgb, size);
            point(w, feet, r, 0.12, y, rgb, size);
        }
        for (double y = 0.8; y <= 1.4; y += 0.2) {
            point(w, feet, r, -0.2, y, rgb, size);
            point(w, feet, r, 0, y, rgb, size);
            point(w, feet, r, 0.2, y, rgb, size);
        }
        for (double s = -0.6; s <= 0.6; s += 0.2) {
            point(w, feet, r, s, 1.35, rgb, size);
        }
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8;
            point(w, feet, r, Math.cos(a) * 0.2, 1.7 + Math.sin(a) * 0.2, rgb, size);
        }
    }

    private static void point(World w, Location feet, Vector right, double side, double y, int rgb, float size) {
        dust(w, feet.getX() + right.getX() * side, feet.getY() + y, feet.getZ() + right.getZ() * side, rgb, size);
    }

    // ------------------------------------------------------------------
    // animation
    // ------------------------------------------------------------------

    /** Runs step(i) for i = 0..count-1, once every `period` ticks. */
    public static void run(JavaPlugin plugin, int count, int period, IntConsumer step) {
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (i >= count) {
                    cancel();
                    return;
                }
                try {
                    step.accept(i++);
                } catch (Throwable t) {
                    cancel();
                    plugin.getLogger().log(Level.WARNING, "An effect failed and was stopped", t);
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    /** Calls body(i) every `period` ticks until it returns false. An error is logged and stops the loop. */
    public static void loop(JavaPlugin plugin, int period, IntPredicate body) {
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                try {
                    if (!body.test(i++)) cancel();
                } catch (Throwable t) {
                    cancel();
                    plugin.getLogger().log(Level.WARNING, "An effect failed and was stopped", t);
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    public static void later(JavaPlugin plugin, long delayTicks, Runnable task) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /** A ring that grows (or shrinks, if toR < fromR) over `ticks` ticks. */
    public static void expandingRing(JavaPlugin plugin, Location center, double fromR, double toR, int ticks,
                                     int rgb, float size, Particle accent) {
        Location c = center.clone();
        run(plugin, ticks, 1, i -> {
            double t = (i + 1) / (double) ticks;
            double r = fromR + (toR - fromR) * t;
            int pts = (int) Math.max(16, Math.abs(r) * 10);
            ringDust(c, r, pts, rgb, size);
            if (accent != null) ring(c, r, (int) Math.max(8, Math.abs(r) * 4), accent);
        });
    }

    /** Two dust strands spiralling up around an entity. */
    public static void spiralUp(JavaPlugin plugin, Entity e, int ticks, double radius,
                                int rgb, float size, Particle accent) {
        run(plugin, ticks, 1, i -> {
            if (!e.isValid()) return;
            Location b = e.getLocation();
            double h = (i % 20) / 20.0 * 2.0;
            for (int k = 0; k < 2; k++) {
                double a = i * 0.5 + k * Math.PI;
                double x = b.getX() + Math.cos(a) * radius;
                double z = b.getZ() + Math.sin(a) * radius;
                double y = b.getY() + h;
                dust(b.getWorld(), x, y, z, rgb, size);
                if (accent != null) b.getWorld().spawnParticle(accent, x, y, z, 1, 0, 0, 0, 0);
            }
        });
    }
}
