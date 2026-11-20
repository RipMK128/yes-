package dev.smp.eclipsepowers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PowerCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "eclipsepowers.admin";

    private final EclipsePowers plugin;
    private final PowerManager manager;
    private final Menus menus;
    private final Roller roller;

    public PowerCommand(EclipsePowers plugin, PowerManager manager, Menus menus, Roller roller) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = menus;
        this.roller = roller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (roller.isRolling(player)) {
                    player.sendMessage(Component.text("Your power is being rolled...", NamedTextColor.GRAY));
                } else {
                    menus.openInfo(player);
                }
            } else {
                sender.sendMessage(Component.text("Use /power list", NamedTextColor.GRAY));
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "set" -> set(sender, args);
            case "kills" -> kills(sender, args);
            case "reroll" -> reroll(sender, args);
            case "reload" -> reload(sender);
            default -> sender.sendMessage(Component.text("Usage: /power [list|set|kills|reroll|reload]",
                    NamedTextColor.RED));
        }
        return true;
    }

    // ---------- /power list ----------

    private void list(CommandSender sender) {
        if (sender instanceof Player player) {
            menus.openCatalog(player);
            return;
        }
        sender.sendMessage(Component.text("Powers:", NamedTextColor.GRAY));
        for (Power power : Power.values()) {
            sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(power.displayName(), power.color()))
                    .append(Component.text(": " + power.abilityName(1) + " / " + power.abilityName(2),
                            NamedTextColor.GRAY)));
        }
    }

    // ---------- admin commands ----------

    private void set(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /power set <player> <power>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Power power = Power.fromName(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        if (power == null) {
            sender.sendMessage(Component.text("Unknown power. Use /power list", NamedTextColor.RED));
            return;
        }
        manager.setPower(target, power);
        manager.awaken(target, power);
        sender.sendMessage(Component.text(target.getName() + " now has " + power.displayName() + ".",
                NamedTextColor.GREEN));
    }

    private void kills(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /power kills <player> <amount>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
            return;
        }
        manager.setKills(target, amount);
        sender.sendMessage(Component.text(target.getName() + " now has " + Math.max(0, amount) + " kills.",
                NamedTextColor.GREEN));
    }

    private void reroll(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /power reroll <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        roller.start(target, null);
        sender.sendMessage(Component.text("Rolling a new power for " + target.getName() + "...",
                NamedTextColor.GREEN));
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadAll();
        sender.sendMessage(Component.text("EclipsePowers config reloaded.", NamedTextColor.GREEN));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
        return false;
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean isAdmin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            out.add("list");
            if (isAdmin) {
                out.add("set");
                out.add("kills");
                out.add("reroll");
                out.add("reload");
            }
        } else if (isAdmin && args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (isAdmin && args.length == 3 && args[0].equalsIgnoreCase("set")) {
            for (Power power : Power.values()) out.add(power.name().toLowerCase(Locale.ROOT));
        }

        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
