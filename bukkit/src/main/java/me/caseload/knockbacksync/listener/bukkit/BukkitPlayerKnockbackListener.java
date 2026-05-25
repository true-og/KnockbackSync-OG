package me.caseload.knockbacksync.listener.bukkit;

import com.github.retrooper.packetevents.util.Vector3d;
import me.caseload.knockbacksync.listener.PlayerKnockbackListener;
import me.caseload.knockbacksync.player.BukkitPlayer;
import me.caseload.knockbacksync.util.MultiLibUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

public class BukkitPlayerKnockbackListener extends PlayerKnockbackListener implements Listener {

    // HIGHEST so KnockbackSync is the last writer in the event chain: it owns the final
    // melee velocity. We read and write the EVENT velocity (not player.getVelocity()/
    // player.setVelocity()) so we integrate with the chain instead of fighting it. See
    // knockback-sync issue #7 (kernitus, Krymonota, Axionize).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player victim = event.getPlayer();
        EntityDamageEvent entityDamageEvent = victim.getLastDamageCause();
        if (entityDamageEvent == null)
            return;

        EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
        if (damageCause != EntityDamageEvent.DamageCause.ENTITY_ATTACK)
            return;

        Entity attacker = ((EntityDamageByEntityEvent) entityDamageEvent).getDamager();
        if (!(attacker instanceof Player))
            return;

        if (MultiLibUtil.isExternalPlayer(victim))
            return;

        Vector eventVelocity = event.getVelocity();
        Vector3d result = onPlayerVelocity(new BukkitPlayer(victim),
                new Vector3d(eventVelocity.getX(), eventVelocity.getY(), eventVelocity.getZ()));

        if (result != null)
            event.setVelocity(new Vector(result.getX(), result.getY(), result.getZ()));
    }
}
