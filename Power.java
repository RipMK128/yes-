package dev.smp.eclipsepowers;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Locale;

public enum Power {
    VOID("Void", 0x8A2BE2, Material.ENDER_EYE, Material.PURPLE_STAINED_GLASS_PANE,
            "Void Pull", 14, "Drags every nearby enemy toward you.",
            "Void Rift", 40, "Opens a rift where you aim that crushes and pulls in enemies for 4 seconds."),
    NULL("Null", 0x9AA0A6, Material.ECHO_SHARD, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            "Null Pulse", 15, "Strips enemies' potion effects and silences them (no abilities) for 6 seconds.",
            "Null Shield", 45, "Nullifies all damage you take for 4 seconds."),
    LIGHT("Light", 0xFFE066, Material.BEACON, Material.YELLOW_STAINED_GLASS_PANE,
            "Light Beam", 10, "Fires a beam that damages and highlights the first enemy it hits.",
            "Solar Flare", 35, "Blinds and burns nearby enemies while healing you."),
    DARK("Dark", 0x6A5ACD, Material.WITHER_ROSE, Material.BLUE_STAINED_GLASS_PANE,
            "Dark Veil", 14, "Plunges nearby enemies into darkness while you gain speed.",
            "Soul Drain", 35, "Withers nearby enemies and steals their life force to heal you."),
    SHADOW("Shadow", 0x8D7B9E, Material.PHANTOM_MEMBRANE, Material.GRAY_STAINED_GLASS_PANE,
            "Shadow Step", 8, "Teleports you forward and makes you briefly invisible.",
            "Shadow Strike", 30, "Teleports behind the nearest enemy and strikes hard."),
    ECLIPSE("Eclipse", 0xFF9F1C, Material.DAYLIGHT_DETECTOR, Material.ORANGE_STAINED_GLASS_PANE,
            "Eclipse Flash", 15, "Blinds and slows all enemies around you.",
            "Totality", 45, "Roots nearby enemies in place, blind and weakened, for 4 seconds."),
    ASTRAL("Astral", 0x4CC9F0, Material.NETHER_STAR, Material.CYAN_STAINED_GLASS_PANE,
            "Astral Dash", 8, "Launches you forward through the air.",
            "Star Fall", 40, "Calls a rain of falling stars on the spot you aim at."),
    CHAOS("Chaos", 0xF72585, Material.FIREWORK_STAR, Material.MAGENTA_STAINED_GLASS_PANE,
            "Chaos Roll", 14, "Inflicts a random curse on each nearby enemy.",
            "Chaos Storm", 40, "Lightning strikes nearby enemies at random."),
    NATURE("Nature", 0x54E36B, Material.OAK_SAPLING, Material.LIME_STAINED_GLASS_PANE,
            "Seed Burst", 12, "Poisons nearby enemies in a burst of seeds.",
            "Wildgrowth", 32, "Regenerates you amid a spiral of living energy."),
    STORM("Storm", 0x7DEBFF, Material.LIGHTNING_ROD, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            "Chain Lightning", 12, "Lightning arcs through nearby enemies.",
            "Thunderstorm", 38, "Calls lightning down on nearby enemies."),
    FROST("Frost", 0x8DEBFF, Material.BLUE_ICE, Material.BLUE_STAINED_GLASS_PANE,
            "Frost Nova", 14, "Freezes nearby enemies and slows them.",
            "Ice Lance", 30, "Fires an ice lance at a target."),
    EARTH("Earth", 0xC98A52, Material.TERRACOTTA, Material.BROWN_STAINED_GLASS_PANE,
            "Seismic Slam", 16, "Damages and launches nearby enemies.",
            "Stoneguard", 42, "Creates a shield surrounded by stone particles."),
    TIME("Time", 0xA98BFF, Material.CLOCK, Material.PURPLE_STAINED_GLASS_PANE,
            "Quickstep", 12, "Grants speed, jumping, and slow falling.",
            "Time Snare", 40, "Slows and reveals nearby enemies.");

    private final String displayName;
    private final int rgb;
    private final TextColor color;
    private final Material icon;
    private final Material pane;
    private final String[] abilityNames;
    private final int[] cooldowns;
    private final String[] descriptions;

    Power(String displayName, int rgb, Material icon, Material pane,
          String name1, int cooldown1, String desc1,
          String name2, int cooldown2, String desc2) {
        this.displayName = displayName;
        this.rgb = rgb;
        this.color = TextColor.color(rgb);
        this.icon = icon;
        this.pane = pane;
        this.abilityNames = new String[]{name1, name2};
        this.cooldowns = new int[]{cooldown1, cooldown2};
        this.descriptions = new String[]{desc1, desc2};
    }

    public String displayName() { return displayName; }
    public TextColor color() { return color; }

    /** The color as a plain 0xRRGGBB number, handy for dust particles. */
    public int rgb() { return rgb; }
    public Material icon() { return icon; }
    public Material pane() { return pane; }

    /** slot is 1 or 2 */
    public String abilityName(int slot) { return abilityNames[slot - 1]; }
    public int cooldownSeconds(int slot) { return cooldowns[slot - 1]; }
    public String description(int slot) { return descriptions[slot - 1]; }

    public static Power fromName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
