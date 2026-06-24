package me.caseload.knockbacksync;

import com.github.retrooper.packetevents.PacketEvents;
import me.caseload.knockbacksync.common.BuildConfig;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitLoaderPlugin extends JavaPlugin {

    private final Base core = new BukkitBase(this);

    @Override
    public void onLoad() {
        core.load();
    }

    @Override
    public void onEnable() {
        core.enable();
    }

    @Override
    public void onDisable() {
        // Reconcile away any leftover owned knockback-resistance marker so no player is left
        // with reduced resistance after the plugin unloads.
        me.caseload.knockbacksync.listener.bukkit.KnockbackResistanceNullifier.cleanupAll();
        // Drop listener handles before any lifecycle decision; the shared API may outlive this plugin.
        core.unregisterPacketListeners();
        // Terminate only when this plugin owns the PacketEvents lifecycle (shaded build).
        if (BuildConfig.SHADE_PE) {
            PacketEvents.getAPI().terminate();
        }
    }
}