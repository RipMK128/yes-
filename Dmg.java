package dev.smp.eclipsepowers;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Damage that is not eaten by armor and Protection enchantments.
 * <p>
 * First a normal hit goes through (knockback, hurt sound, kill credit, other plugins' protections).
 * Then whatever armor, enchantments or Resistance took off is added back as "true" damage, so the
 * target loses the full amount no matter what they wear. If the normal hit was cancelled
 * (Null Shield, a no-PvP region, ...) nothing is added back.
 */
public final class Dmg {

    private static boolean trueDamage = true;
    private static LivingEntity probe;
    private static boolean fired;
    private static boolean cancelled;

    private Dmg() {
    }

    public static void setTrueDamage(boolean value) {
        trueDamage = value;
    }

    /** Called from a MONITOR listener so we know what happened to the normal hit. */
    public static void observe(EntityDamageEvent event) {
        if (probe != null && event.getEntity() == probe) {
            fired = true;
            cancelled = event.isCancelled();
        }
    }

    public static void hit(LivingEntity target, Player source, double amount) {
        if (amount <= 0 || target == null || target.isDead() || !target.isValid()) return;

        if (!trueDamage) {
            target.damage(amount, source);
            return;
        }
        if (target instanceof Player victim
                && (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR)) {
            target.damage(amount, source);
            return;
        }

        double before = target.getHealth() + target.getAbsorptionAmount();
        probe = target;
        fired = false;
        cancelled = false;
        try {
            target.damage(amount, source);
        } finally {
            probe = null;
        }
        // no event = the hit was ignored (invulnerability frames); cancelled = something blocked it
        if (!fired || cancelled) return;
        if (target.isDead() || !target.isValid()) return;

        double lost = before - (target.getHealth() + target.getAbsorptionAmount());
        double missing = amount - lost;
        if (missing <= 0.05) return;

        double pool = target.getHealth() + target.getAbsorptionAmount();
        if (missing >= pool) {
            // lethal: let the normal death logic run (totems, death message, kill credit)
            target.damage(100000.0, source);
            return;
        }
        double absorption = target.getAbsorptionAmount();
        double fromAbsorption = Math.min(absorption, missing);
        if (fromAbsorption > 0) {
            target.setAbsorptionAmount(absorption - fromAbsorption);
            missing -= fromAbsorption;
        }
        if (missing > 0) {
            target.setHealth(Math.max(0.1, target.getHealth() - missing));
        }
    }
}
