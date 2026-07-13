package me.caseload.knockbacksync.manager;

import lombok.Getter;
import lombok.Setter;
import me.caseload.knockbacksync.ConfigWrapper;
import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.Platform;
import me.caseload.knockbacksync.config.YamlConfiguration;
import me.caseload.knockbacksync.runnable.PingRunnable;
import me.caseload.knockbacksync.scheduler.AbstractTaskHandle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ConfigManager {

    public static final long CONFIG_VERSION = 9;

    private boolean toggled;
    private boolean runnableEnabled;
    private boolean updateAvailable;
    private boolean notifyUpdate;
    private boolean autoUpdate;

    // Legacy (1.8) knockback: makes KnockbackSync the single owner of melee velocity,
    // reproducing OldCombatMechanics' old-player-knockback values while still latency-syncing.
    private boolean legacyKnockbackEnabled;
    private double legacyKnockbackHorizontal;
    private double legacyKnockbackVertical;
    private double legacyKnockbackVerticalLimit;
    private double legacyKnockbackExtraHorizontal;
    private double legacyKnockbackExtraVertical;
    private boolean legacyKnockbackResistanceEnabled;

    private long runnableInterval;
    private long combatTimer;
    private long spikeThreshold;

    private String enableMessage;
    private String disableMessage;
    private String playerEnableMessage;
    private String playerDisableMessage;
    private String playerIneligibleMessage;
    private String playerDisconnectedWhileExecutingCommand;

    private AbstractTaskHandle pingTask;

    private Map<String, Object> config;
    private File configFile;
    private ConfigWrapper configWrapper; // Cache the ConfigWrapper instance
    private YamlConfiguration yamlConfig;

    public ConfigManager() {
        Base instance = Base.INSTANCE;
        configFile = new File(instance.getDataFolder(), "config.yml");
        yamlConfig = new YamlConfiguration(configFile);
    }

    public ConfigWrapper getConfigWrapper() {
        if (configWrapper == null) {
            reloadConfig();
        }
        return configWrapper;
    }

    public void reloadConfig() {
        try {
            if (!configFile.exists()) {
                Base.INSTANCE.saveDefaultConfig();
            }
            yamlConfig.load();
            config = yamlConfig.getData();
            configWrapper = new ConfigWrapper(config);
        } catch (IOException e) {
            e.printStackTrace();
            config = new HashMap<>();
            configWrapper = new ConfigWrapper(config);
        }
    }

    public void saveConfig() {
        try {
            yamlConfig.setData(config);
            yamlConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadConfig(boolean reloadConfig) {
        if (reloadConfig || config == null) {
            reloadConfig();
        }

        ConfigWrapper configWrapper = getConfigWrapper(); // Use cached ConfigWrapper

        updateConfig();

        toggled = configWrapper.getBoolean("enabled", true);

        boolean newRunnableEnabled = configWrapper.getBoolean("runnable.enabled", true);
        // Always stop the previous ping task on reload (reschedule below only if still enabled); gating the cancel on staying enabled leaked the task when disabling via reload.
        if (pingTask != null) { // null on first startup
            pingTask.cancel();
            pingTask = null;
        }

        runnableEnabled = newRunnableEnabled;
        runnableInterval = configWrapper.getLong("runnable.interval", 5L);

        if (runnableEnabled) {
            long initialDelay = 0L;
            long pingTaskRunnableInterval = runnableInterval;
            // Folia does not allow 0 ticks of wait time
            if (Base.INSTANCE.getPlatform() == Platform.FOLIA) {
                initialDelay = 1L;
                pingTaskRunnableInterval = Math.max(pingTaskRunnableInterval, 1L);
            }
            pingTask = Base.INSTANCE.getScheduler().runTaskTimerAsynchronously(new PingRunnable(), initialDelay, pingTaskRunnableInterval);
        }

        legacyKnockbackEnabled = configWrapper.getBoolean("legacy_knockback.enabled", false);
        legacyKnockbackHorizontal = configWrapper.getDouble("legacy_knockback.horizontal", 0.4);
        legacyKnockbackVertical = configWrapper.getDouble("legacy_knockback.vertical", 0.4);
        legacyKnockbackVerticalLimit = configWrapper.getDouble("legacy_knockback.vertical_limit", 0.4);
        legacyKnockbackExtraHorizontal = configWrapper.getDouble("legacy_knockback.extra_horizontal", 0.5);
        legacyKnockbackExtraVertical = configWrapper.getDouble("legacy_knockback.extra_vertical", 0.1);
        legacyKnockbackResistanceEnabled = configWrapper.getBoolean("legacy_knockback.enable_knockback_resistance", false);

        notifyUpdate = configWrapper.getBoolean("notify_updates", true);
        autoUpdate = configWrapper.getBoolean("auto_update", true);
        combatTimer = configWrapper.getLong("runnable.combat_timer", 30L);
        spikeThreshold = configWrapper.getLong("spike_threshold", 20L);
        enableMessage = configWrapper.getString("messages.toggle.global.enable", "&aSuccessfully enabled KnockbackSync.");
        disableMessage = configWrapper.getString("messages.toggle.global.disable", "&cSuccessfully disabled KnockbackSync.");
        playerEnableMessage = configWrapper.getString("messages.toggle.player.enable", "&aSuccessfully enabled KnockbackSync for %player%.");
        playerDisableMessage = configWrapper.getString("messages.toggle.player.disable", "&aSuccessfully &cdisabled &aKnockbackSync for %player%.");
        playerIneligibleMessage = configWrapper.getString("messages.toggle.player.ineligible", "&c%player% is ineligible for KnockbackSync. If you believe this is in error, please contact your server administrators.");
        playerDisconnectedWhileExecutingCommand = configWrapper.getString("messages.toggle.player.disconnected-while-executing-command", "&c%player% disconnected while executing command.");
    }

    public void updateConfig() {
        ConfigWrapper oldConfig = getConfigWrapper();
        long oldConfigVersion = oldConfig.getLong("config_version", 0);

        if (oldConfigVersion < CONFIG_VERSION) {
            // Backup old config with comments
            File backupFile = new File(configFile.getParentFile(), "config-version-" + oldConfigVersion + ".yml");
            try {
                Files.move(configFile.toPath(), backupFile.toPath());
                Base.INSTANCE.getLogger().info("Backed up old config to " + backupFile.getName());
            } catch (IOException e) {
                Base.INSTANCE.getLogger().warning("Failed to backup old config: " + e.getMessage());
            }

            // Store old values
            Map<String, Object> oldValues = new HashMap<>(config);

            // Create new config with default values
            Base.INSTANCE.saveDefaultConfig();
            reloadConfig();

            // Transfer existing settings
            ConfigWrapper newConfig = getConfigWrapper();
            transferAllSettings(oldConfig, newConfig);

            // Set new config version
            newConfig.set("config_version", CONFIG_VERSION);

            // Save updated config
            saveConfig();

            Base.INSTANCE.getLogger().info("Config updated to version " + CONFIG_VERSION);
        }
    }

    private void transferAllSettings(ConfigWrapper oldConfig, ConfigWrapper newConfig) {
        transferSettingsRecursive(oldConfig, newConfig, ".");
    }

    private void transferSettingsRecursive(ConfigWrapper oldConfig, ConfigWrapper newConfig, String currentPath) {
        for (String key : oldConfig.getKeys(currentPath)) {
            String fullPath = currentPath.isEmpty() || currentPath.equals(".") ? key : currentPath + "." + key;
            if (newConfig.contains(fullPath)) {
                Object value = oldConfig.get(fullPath);
                if (value instanceof Map) {
                    transferSettingsRecursive(oldConfig, newConfig, fullPath);
                } else {
                    newConfig.set(fullPath, value);
                }
            }
        }
    }
}