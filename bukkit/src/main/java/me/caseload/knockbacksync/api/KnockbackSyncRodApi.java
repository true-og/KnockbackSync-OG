package me.caseload.knockbacksync.api;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.manager.PlayerDataManager;
import me.caseload.knockbacksync.player.BukkitPlayer;
import me.caseload.knockbacksync.player.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Stable, reflection-friendly entry point for other plugins (notably OldCombatMechanics'
 * old-fishing-knockback module) to hand KnockbackSync a pre-computed knockback vector, so that
 * KnockbackSync owns the final velocity and applies the same latency Y-correction it gives melee
 * knockback.
 *
 * <p>Why this exists: a fishing-rod hit reaches KnockbackSync as an ordinary player-vs-player
 * damage event. KnockbackSync cannot tell it apart from a sword hit, so it computes a melee vector
 * and the caller's own {@code setVelocity} either fights it or is re-corrected with melee-tuned
 * latency logic — the rod pop ends up flattened/inconsistent under latency. Routing the
 * bobber-direction vector through here makes the rod feel like 1.8, latency-synced like melee.</p>
 *
 * <p>Resolved by callers via reflection; keep the class name, package, and method signature
 * stable.</p>
 */
public final class KnockbackSyncRodApi {

    private KnockbackSyncRodApi() {
    }

    /**
     * Registers a fishing-rod knockback vector for {@code victim}, to be applied and latency-synced
     * by the {@code PlayerVelocityEvent} produced by the caller's subsequent damage tick.
     *
     * <p>MUST be called on the main thread, immediately before the caller deals the rod's damage.</p>
     *
     * @param victim        the player being knocked back
     * @param rodKnockback  the 1.8 bobber-direction knockback vector
     * @return {@code true} if KnockbackSync will own this knockback (caller must NOT also call
     *         {@code setVelocity}); {@code false} if KnockbackSync is disabled or not tracking this
     *         player, in which case the caller should apply the knockback itself.
     */
    public static boolean applyRodKnockback(Player victim, Vector rodKnockback) {
        if (victim == null || rodKnockback == null) return false;

        final Base base = Base.INSTANCE;
        if (base == null) return false;

        final ConfigManager config = base.getConfigManager();
        if (config == null || !config.isToggled() || !config.isLegacyKnockbackEnabled())
            return false;

        final User user = new BukkitPlayer(victim).getUser();
        if (user == null) return false;

        final PlayerData playerData = PlayerDataManager.getPlayerData(user);
        if (playerData == null) return false; // not tracked (exempt/Geyser/etc.) — caller falls back

        final Vector3d kb = new Vector3d(rodKnockback.getX(), rodKnockback.getY(), rodKnockback.getZ());
        playerData.setRodKnockback(kb);
        // On-ground sync should target the rod's vertical, mirroring the melee damage path.
        playerData.setVerticalVelocity(kb.getY());
        return true;
    }
}
