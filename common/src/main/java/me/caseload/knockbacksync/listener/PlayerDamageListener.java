package me.caseload.knockbacksync.listener;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.manager.PlayerDataManager;
import me.caseload.knockbacksync.player.PlatformPlayer;
import me.caseload.knockbacksync.player.PlayerData;
import me.caseload.knockbacksync.util.LegacyKnockback;

public abstract class PlayerDamageListener {
    public void onPlayerDamage(PlatformPlayer victim, PlatformPlayer attacker) {
        ConfigManager config = Base.INSTANCE.getConfigManager();
        if (!config.isToggled())
            return;

        User user = victim.getUser();
        if (user == null) return; // Prevent errors with players disconnecting while this is running (or with fake player?)

        PlayerData playerData = PlayerDataManager.getPlayerData(user);
        if (playerData == null)
            return;

        if (config.isLegacyKnockbackEnabled()) {
            // KnockbackSync owns the full 1.8 melee velocity. Compute it now (we have the
            // attacker here) and store it for the PlayerVelocityEvent to apply + sync.
            Vector3d legacy = LegacyKnockback.compute(victim, attacker, playerData, config);
            playerData.setLegacyKnockback(legacy);
            // On-ground sync should target the legacy vertical value, not the 1.9 one.
            playerData.setVerticalVelocity(legacy.getY());
        } else {
            playerData.setVerticalVelocity(playerData.calculateVerticalVelocity(attacker)); // do not move this calculation
        }
        playerData.setLastDamageTicks(victim.getNoDamageTicks());
        playerData.updateCombat();

        if (!Base.INSTANCE.getConfigManager().isRunnableEnabled())
            playerData.sendPing(true);
    }
}
