package dev.smp.eclipsepowers;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class EclipsePowers extends JavaPlugin {

    private PowerManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Fx.setDensity(getConfig().getDouble("particle-density", 1.0));
        Dmg.setTrueDamage(getConfig().getBoolean("true-damage", true));

        manager = new PowerManager(this);
        Menus menus = new Menus(manager);
        Roller roller = new Roller(this, manager);

        getServer().getPluginManager().registerEvents(new PowerListener(this, manager, menus, roller), this);

        PowerCommand command = new PowerCommand(this, manager, menus, roller);
        PluginCommand power = getCommand("power");
        if (power != null) {
            power.setExecutor(command);
            power.setTabCompleter(command);
        }

        getLogger().info("EclipsePowers enabled.");

        // every player's power shows as a subtle aura around them
        if (getConfig().getBoolean("aura", true)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Power power = manager.getPower(p);
                        if (power != null) manager.abilities().aura(p, power);
                    }
                }
            }.runTaskTimer(this, 40L, 4L);
        }
    }

    /** /power reload: re-reads config.yml and applies it without a restart. */
    public void reloadAll() {
        reloadConfig();
        Fx.setDensity(getConfig().getDouble("particle-density", 1.0));
        Dmg.setTrueDamage(getConfig().getBoolean("true-damage", true));
        manager.abilities().reloadSettings();
    }

    public PowerManager manager() {
        return manager;
    }
}
