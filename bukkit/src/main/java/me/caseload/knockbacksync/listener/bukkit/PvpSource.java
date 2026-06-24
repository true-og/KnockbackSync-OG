package me.caseload.knockbacksync.listener.bukkit;

import com.github.retrooper.packetevents.protocol.player.User;
import me.caseload.knockbacksync.manager.PlayerDataManager;
import me.caseload.knockbacksync.player.BukkitPlayer;
import me.caseload.knockbacksync.player.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * D1 — PvP Source Attribution.
 *
 * <p>Classifies a damage event on a player victim into a source class so the
 * knockback-resistance nullifier (D3) knows whether and how to act. Attribution is
 * self-inclusive: the victim's own projectile (e.g. their own arrow) still counts as
 * PvP. This classifier only answers "is this player-attributed, and by what source
 * class?" — it does not modify damage or knockback.</p>
 *
 * <p>Scope note (Purpur 1.19.4 target): netherite {@code generic.knockback_resistance}
 * only reduces knockback applied through {@code LivingEntity.knockback(...)} (melee and
 * projectile). Explosion knockback is not reduced by it on 1.19.4 (Blast Protection
 * governs that), and wind charges do not exist, so explosion/wind-charge sources are
 * classified {@link Type#NONE}.</p>
 */
public final class PvpSource {

    public enum Type {
        /** Direct player melee. Owned by {@code LegacyKnockback}; no attribute nullification. */
        MELEE,
        /** Player-shot projectile (arrow, spectral arrow, trident, snowball, egg, ender pearl, ...). Nullified by D3. */
        PROJECTILE,
        /** Fishing-rod hit (OCM {@code old-fishing-knockback}); owned by the rod sync. No nullification. */
        FISHING,
        /** Not attributable to a player (mob, environment, explosion, ...). */
        NONE
    }

    private PvpSource() {
    }

    /**
     * @param event a damage-by-entity event (the caller has not necessarily checked the victim type)
     * @return the PvP source class; {@link Type#NONE} when the damage is not attributable to a player
     */
    public static Type classify(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return Type.NONE;
        final Player victim = (Player) event.getEntity();
        final Entity damager = event.getDamager();

        // Direct player damager: either a melee hit, or an OCM fishing-rod hit.
        // old-fishing-knockback deals its damage via damage(victim, rodder), so the
        // damager is the rodding player (not the bobber). Distinguish the rod hit via
        // the rod-pending marker KnockbackSync stores for the rod path. Both MELEE and
        // FISHING are owned elsewhere and get no attribute nullification, so a mislabel
        // between the two cannot change knockback behaviour.
        if (damager instanceof Player) {
            return isRodPending(victim) ? Type.FISHING : Type.MELEE;
        }

        // Player-shot projectile, resolved generically via ProjectileSource so any
        // player-shot projectile type is covered without a per-type list (including
        // types absent from the compile-time API). Self-inclusive: the shooter may be
        // the victim. A non-player projectile source (e.g. a dispenser's
        // BlockProjectileSource) or a null shooter is not PvP. Note: the ender pearl's
        // post-teleport fall damage is a separate non-by-entity event, so it never
        // reaches this projectile branch.
        if (damager instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) damager).getShooter();
            return (shooter instanceof Player) ? Type.PROJECTILE : Type.NONE;
        }

        // Explosions (TNT, end crystal, fireball), mobs, environment: out of scope on 1.19.4.
        return Type.NONE;
    }

    private static boolean isRodPending(Player victim) {
        final User user = new BukkitPlayer(victim).getUser();
        if (user == null) return false;
        final PlayerData data = PlayerDataManager.getPlayerData(user);
        return data != null && data.isRodKnockbackPending();
    }
}
