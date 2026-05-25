package me.caseload.knockbacksync.listener.bukkit;

import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.listener.PlayerDamageListener;
import me.caseload.knockbacksync.player.BukkitPlayer;
import me.caseload.knockbacksync.util.MultiLibUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BukkitPlayerDamageListener extends PlayerDamageListener implements Listener {

    private static boolean knockbackResistanceStripUnsupported = false;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity attacker = event.getDamager();
        if (!(victim instanceof Player) || !(attacker instanceof Player))
            return;

        if (MultiLibUtil.isExternalPlayer((Player) victim))
            return;

        // Legacy (1.8) parity: with knockback resistance disabled (OCM default), nullify the
        // victim's knockback resistance so full 1.8 knockback applies and the velocity event
        // always fires. Must run before vanilla computes knockback (default priority is fine).
        if (Base.INSTANCE.getConfigManager().isLegacyKnockbackEnabled()
                && !Base.INSTANCE.getConfigManager().isLegacyKnockbackResistanceEnabled()) {
            stripKnockbackResistance((Player) victim);
        }

        onPlayerDamage(new BukkitPlayer((Player) victim), new BukkitPlayer((Player) attacker));
    }

    private void stripKnockbackResistance(Player victim) {
        if (knockbackResistanceStripUnsupported) return;
        try {
            AttributeInstance attribute = victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (attribute == null) return;
            attribute.getModifiers().forEach(attribute::removeModifier);
        } catch (Throwable t) {
            // Attribute enum/constant differs on this server version; disable to avoid log spam.
            knockbackResistanceStripUnsupported = true;
            Base.INSTANCE.getLogger().warning("Could not nullify knockback resistance for legacy knockback: " + t.getMessage());
        }
    }
}
