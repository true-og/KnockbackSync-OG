package me.caseload.knockbacksync.listener.bukkit;

import me.caseload.knockbacksync.Base;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * D3 — Resistance Nullification (M1).
 *
 * <p>For player-vs-player projectile hits (classified by {@link PvpSource}), forces the
 * victim's effective {@code generic.knockback_resistance} to 0 for the duration of the
 * hit by adding a single plugin-owned {@code MULTIPLY_SCALAR_1} {@code -1.0} modifier,
 * lets vanilla compute full knockback, then removes the owned marker on the next tick.
 * Equipment and other plugins' modifiers are never read, captured, or removed — only the
 * owned marker (identified by a constant UUID) is added/removed.</p>
 *
 * <p>Melee PvP is owned by {@code LegacyKnockback} (which already ignores resistance when
 * {@code enable_knockback_resistance=false}) and needs no attribute work; fishing PvP is
 * owned by OCM's {@code old-fishing-knockback}. Explosions are out of scope on 1.19.4.</p>
 *
 * <p>The marker is permanent NMS state until removed, so a leftover marker is reconciled
 * away on join/quit and on plugin enable/disable (see {@link #cleanupAll()}), guaranteeing
 * a player is never left permanently with reduced resistance.</p>
 */
public final class KnockbackResistanceNullifier implements Listener {

    // Constant, plugin-owned modifier identity. Add skips if this UUID is already present;
    // removal matches by this UUID. Equipment/foreign modifiers are never touched.
    private static final UUID MODIFIER_UUID = UUID.fromString("7b2a1c84-4d3e-4f0a-9c6b-1e5a9d8c7f30");
    private static final String MODIFIER_NAME = "knockbacksync_pvp_kb_nullify";

    // Mirrors the previous stripKnockbackResistance guard: if the attribute is unavailable
    // on this server version, disable for the session and warn once (no per-hit log spam).
    private static volatile boolean unsupported = false;

    /**
     * Force the victim's effective knockback resistance to 0 for the current hit by adding the
     * owned marker, then schedule its removal on the next tick. Idempotent and re-entrancy safe:
     * if the marker is already present (e.g. a second hit in the same tick) the add is skipped.
     *
     * <p>Must be called before vanilla computes the hit's knockback (i.e. during the damage
     * event at an early priority).</p>
     */
    public static void nullify(Player victim) {
        final AttributeInstance attribute = resolve(victim);
        if (attribute == null) return;
        if (hasOwnedModifier(attribute)) return;

        try {
            attribute.addModifier(new AttributeModifier(
                    MODIFIER_UUID, MODIFIER_NAME, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        } catch (IllegalArgumentException alreadyApplied) {
            return; // raced — modifier already present; nothing to add
        }

        // Remove on the NEXT tick. Same-event (MONITOR/finally) removal is forbidden: vanilla
        // computes the knockback after the event returns, so an in-event removal would restore
        // the attribute before vanilla reads it. On the non-Folia target this is a main-thread
        // next-tick task; it stays compile-safe (no Player#getScheduler()).
        Base.INSTANCE.getScheduler().runTaskLater(() -> remove(victim), 1L);
    }

    /** Remove the owned marker if present. Idempotent; never touches foreign/equipment modifiers. */
    public static void remove(Player victim) {
        final AttributeInstance attribute = resolve(victim);
        if (attribute == null) return;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (MODIFIER_UUID.equals(modifier.getUniqueId())) {
                attribute.removeModifier(modifier);
            }
        }
    }

    /** Strip the owned marker from every online player (plugin enable/disable reconciliation). */
    public static void cleanupAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
    }

    private static boolean hasOwnedModifier(AttributeInstance attribute) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (MODIFIER_UUID.equals(modifier.getUniqueId())) return true;
        }
        return false;
    }

    private static AttributeInstance resolve(Player victim) {
        if (unsupported || victim == null) return null;
        try {
            return victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        } catch (Throwable t) {
            unsupported = true;
            Base.INSTANCE.getLogger().warning(
                    "Could not access KNOCKBACK_RESISTANCE attribute; PvP knockback-resistance "
                            + "nullification disabled for this session: " + t.getMessage());
            return null;
        }
    }

    // ── R7 fail-safe: reconcile away any leftover owned marker ──────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }
}
