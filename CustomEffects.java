package dev.smp.eclipsepowers;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom, model-based visual effects. These use server-side display entities and
 * custom item-model textures supplied by the EclipsePowers resource pack.
 * Vanilla particles are only used as subtle supporting accents.
 */
public final class CustomEffects {
    private static final String MODEL_PREFIX = "eclipsepowers:";
    private static final Map<Power, String> MODELS = new HashMap<>();

    static {
        MODELS.put(Power.VOID, "void_rift");
        MODELS.put(Power.NULL, "null_prism");
        MODELS.put(Power.LIGHT, "sun_core");
        MODELS.put(Power.DARK, "dark_crown");
        MODELS.put(Power.SHADOW, "shadow_blade");
        MODELS.put(Power.ECLIPSE, "eclipse_disk");
        MODELS.put(Power.ASTRAL, "astral_star");
        MODELS.put(Power.CHAOS, "chaos_orb");
        MODELS.put(Power.NATURE, "nature_bloom");
        MODELS.put(Power.STORM, "storm_vortex");
        MODELS.put(Power.FROST, "frost_crystal");
        MODELS.put(Power.EARTH, "earth_shard");
        MODELS.put(Power.TIME, "time_clock");
    }

    private CustomEffects() { }

    public static void aura(Player player, Power power) {
        if (!player.isOnline() || player.isDead() || player.isInvisible()) return;
        Location base = player.getLocation().add(0, 1.15, 0);
        double time = System.currentTimeMillis() / 650.0;

        switch (power) {
            case VOID -> orbitModel(player, base, 0.85, time, 0.95, "void_shard", 0.7);
            case NULL -> ringModel(player, base.clone().add(0, 0.35, 0), 0.75, time * 0.35, "null_prism", 0.55);
            case LIGHT -> orbitModel(player, base, 0.62, time, 0.3, "sun_core", 0.45);
            case DARK -> orbitModel(player, base, 0.72, -time * 0.8, 0.7, "dark_crown", 0.55);
            case SHADOW -> ringModel(player, base.clone().add(0, -0.15, 0), 0.82, -time, "shadow_blade", 0.55);
            case ECLIPSE -> orbitModel(player, base.clone().add(0, 0.55, 0), 0.74, time * 0.6, 0.0, "eclipse_disk", 0.62);
            case ASTRAL -> orbitModel(player, base, 0.82, time * 1.1, 0.25, "astral_star", 0.5);
            case CHAOS -> {
                orbitModel(player, base, 0.74, time * 1.5, 0.0, "chaos_orb", 0.48);
                orbitModel(player, base, 0.74, -time * 1.1 + 2.1, 0.7, "chaos_orb", 0.48);
            }
            case NATURE -> ringModel(player, base.clone().add(0, -0.55, 0), 0.78, time * 0.55, "nature_bloom", 0.58);
            case STORM -> orbitModel(player, base, 0.72, time * 1.8, 0.5, "storm_vortex", 0.52);
            case FROST -> ringModel(player, base.clone().add(0, 0.15, 0), 0.72, time * 0.45, "frost_crystal", 0.56);
            case EARTH -> ringModel(player, base.clone().add(0, -0.85, 0), 0.72, time * 0.25, "earth_shard", 0.62);
            case TIME -> orbitModel(player, base, 0.68, time * 0.4, 0.4, "time_clock", 0.58);
        }
    }

    public static void cast(Player player, Power power, int slot) {
        Location center = player.getLocation().add(0, 1, 0);
        double scale = slot == 2 ? 1.45 : 1.0;
        String model = MODELS.getOrDefault(power, "eclipse_sigil");

        ItemDisplay display = player.getWorld().spawn(center, ItemDisplay.class, entity -> {
            entity.setItemStack(customItem(model));
            entity.setBillboard(ItemDisplay.Billboard.CENTER);
            entity.setViewRange(96);
            entity.setShadowRadius(0.08f);
            entity.setPersistent(false);
            entity.setTeleportDuration(1);
        });

        display.setTransformationMatrix(display.getTransformation().getMatrix());
        Fx.run(player.getServer().getPluginManager().getPlugin("EclipsePowers"), 20, 1, i -> {
            if (!display.isValid()) return;
            double t = i / 20.0;
            double radius = (0.15 + t * 1.8) * scale;
            double angle = i * 0.34;
            Location point = center.clone().add(Math.cos(angle) * radius,
                    Math.sin(angle * 0.7) * 0.45 + t * 1.1,
                    Math.sin(angle) * radius);
            display.teleport(point);
            display.setRotation((float) Math.toDegrees(angle), (float) (90 * t));
            if (i == 19) display.remove();
        });

        center.getWorld().spawnParticle(Particle.GUST, center, Fx.n(slot == 2 ? 8 : 4),
                0.35, 0.35, 0.35, 0.02);
    }

    private static void orbitModel(Player player, Location center, double radius, double angle,
                                   double height, String model, double scale) {
        Location point = center.clone().add(Math.cos(angle) * radius,
                height + Math.sin(angle * 2) * 0.08, Math.sin(angle) * radius);
        spawnTransientModel(player, point, model, scale);
    }

    private static void ringModel(Player player, Location center, double radius, double angle,
                                  String model, double scale) {
        double half = Math.PI / 2;
        spawnTransientModel(player, center.clone().add(Math.cos(angle) * radius, 0,
                Math.sin(angle) * radius), model, scale);
        spawnTransientModel(player, center.clone().add(Math.cos(angle + half) * radius, 0,
                Math.sin(angle + half) * radius), model, scale);
    }

    private static void spawnTransientModel(Player player, Location location, String model, double scale) {
        player.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(customItem(model));
            display.setBillboard(ItemDisplay.Billboard.CENTER);
            display.setViewRange(48);
            display.setPersistent(false);
            display.setShadowRadius(0);
            display.setScale(new org.bukkit.util.Transformation());
        });
    }

    private static ItemStack customItem(String model) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(org.bukkit.NamespacedKey.fromString(MODEL_PREFIX + model));
            item.setItemMeta(meta);
        }
        return item;
    }
}
