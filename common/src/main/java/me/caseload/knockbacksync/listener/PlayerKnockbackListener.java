package me.caseload.knockbacksync.listener;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.manager.PlayerDataManager;
import me.caseload.knockbacksync.player.PlatformPlayer;
import me.caseload.knockbacksync.player.PlayerData;
import org.jetbrains.annotations.Nullable;

public abstract class PlayerKnockbackListener {

    /**
     * Computes the melee velocity to apply to the victim.
     *
     * <p>The returned value, when non-null, MUST be written back by the platform layer.
     * Bukkit writes it to the {@code PlayerVelocityEvent} (so KnockbackSync is the last,
     * authoritative writer in the event chain rather than reading/writing the player's
     * live velocity out-of-band — see issue #7).</p>
     *
     * <p>When legacy (1.8) knockback is enabled, the 1.8 vector computed at damage time
     * replaces vanilla X/Z/Y, and latency Y-correction is applied on top of it. In that
     * mode this method always returns a velocity (the raw 1.8 vector when no correction
     * applies), so KnockbackSync owns the final melee velocity.</p>
     *
     * @return the velocity to apply, or {@code null} to leave the velocity unchanged.
     */
    @Nullable
    public Vector3d onPlayerVelocity(PlatformPlayer victim, Vector3d velocity) {
        ConfigManager config = Base.INSTANCE.getConfigManager();
        if (!config.isToggled())
            return null;

        User user = victim.getUser();
        if (user == null) return null; // Prevent errors with players disconnecting while this is running (or with fake player?)

        PlayerData victimPlayerData = PlayerDataManager.getPlayerData(user);
        if (victimPlayerData == null)
            return null;

        // Legacy 1.8 knockback: KnockbackSync owns the melee velocity. The 1.8 vector
        // (computed at damage time) replaces vanilla X/Z/Y. We still apply latency
        // Y-correction on top when conditions allow; otherwise the raw 1.8 vector is
        // applied. legacyKb == null means either legacy is off or none is pending, in
        // which case behaviour falls back to the original (Y-sync of the event velocity).
        Vector3d legacyKb = config.isLegacyKnockbackEnabled() ? victimPlayerData.consumeLegacyKnockback() : null;
        Vector3d base = (legacyKb != null) ? legacyKb : velocity;

        if (victimPlayerData.getNotNullPing() < PlayerData.PING_OFFSET)
            return legacyKb;

        double distanceToGround = victimPlayerData.getDistanceToGround();
        if (distanceToGround <= 0)
            return legacyKb; // minecraft already does the work for us

        WrappedBlockState blockState = victim.getWorld().getBlockStateAt(victim.getLocation());
        if (victim.isGliding() ||
                blockState.getType() == StateTypes.WATER ||
                blockState.getType() == StateTypes.LAVA ||
                blockState.getType() == StateTypes.COBWEB ||
                blockState.getType() == StateTypes.SCAFFOLDING)
            return legacyKb;

        Vector3d adjustedVelocity;
        if (victimPlayerData.isOnGroundClientSide(base.getY(), distanceToGround)) {
            Integer damageTicks = victimPlayerData.getLastDamageTicks();
            if (damageTicks != null && damageTicks > 8)
                return legacyKb;

            adjustedVelocity = base.withY(victimPlayerData.getVerticalVelocity()); // Should be impossible to produce a NPE in this context
        }
        else if (victimPlayerData.isOffGroundSyncEnabled())
            adjustedVelocity = base.withY(victimPlayerData.getCompensatedOffGroundVelocity());
        else
            return legacyKb;

        return adjustedVelocity;
    }
}
