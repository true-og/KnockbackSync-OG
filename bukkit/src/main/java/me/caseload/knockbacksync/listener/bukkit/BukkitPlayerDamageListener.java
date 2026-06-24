package me.caseload.knockbacksync.listener.bukkit;

import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.listener.PlayerDamageListener;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.player.BukkitPlayer;
import me.caseload.knockbacksync.util.MultiLibUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BukkitPlayerDamageListener extends PlayerDamageListener implements Listener {

    /**
     * D3 — projectile PvP knockback-resistance nullification.
     *
     * <p>For a hit that {@link PvpSource} classifies as {@code PROJECTILE} PvP, zero the
     * victim's effective knockback resistance BEFORE vanilla computes the knockback, so a
     * netherite victim takes full (diamond-level) projectile knockback. Runs at {@code LOW}
     * so it precedes OCM's {@code projectile-knockback} damage raise ({@code NORMAL}) and
     * vanilla knockback. The decision is gated on classification + config only — never on the
     * current event damage, because OCM may not have raised snowball/egg/ender-pearl damage
     * yet at this priority.</p>
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProjectilePvpDamage(EntityDamageByEntityEvent event) {
        final ConfigManager config = Base.INSTANCE.getConfigManager();
        // Single source of truth: active only when toggled on, legacy knockback enabled,
        // and knockback resistance is NOT being kept (matching OCM's default).
        if (!config.isToggled() || !config.isLegacyKnockbackEnabled() || config.isLegacyKnockbackResistanceEnabled())
            return;

        if (PvpSource.classify(event) != PvpSource.Type.PROJECTILE)
            return;

        final Player victim = (Player) event.getEntity();
        if (MultiLibUtil.isExternalPlayer(victim))
            return;

        KnockbackResistanceNullifier.nullify(victim);
    }

    /**
     * Melee PvP: KnockbackSync owns the 1.8 melee velocity via {@code LegacyKnockback}
     * (applied at {@code PlayerVelocityEvent}), so melee already delivers full knockback
     * without touching the resistance attribute. The previous {@code stripKnockbackResistance()}
     * (which removed the victim's {@code KNOCKBACK_RESISTANCE} modifiers and never restored them,
     * leaking into PvE) has been deleted. Projectile/explosion/fishing hits are NOT routed
     * through this melee path (their damager is not a player), so no stale legacy-knockback
     * vector can be stored for them.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity attacker = event.getDamager();
        if (!(victim instanceof Player) || !(attacker instanceof Player))
            return;

        if (MultiLibUtil.isExternalPlayer((Player) victim))
            return;

        onPlayerDamage(new BukkitPlayer((Player) victim), new BukkitPlayer((Player) attacker));
    }
}
