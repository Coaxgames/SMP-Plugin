package com.coaxgames.smp;

import com.coaxgames.smp.ability.PhaseFourDailyEffectManager;
import com.coaxgames.smp.command.GalaxyCommand;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.item.PhaseFourItems;
import com.coaxgames.smp.item.PhaseFiveMaces;
import com.coaxgames.smp.listener.LegendaryCraftListener;
import com.coaxgames.smp.listener.PhaseFiveMaceAbilityListener;
import com.coaxgames.smp.listener.PhaseFourAbilityListener;
import com.coaxgames.smp.listener.LegendaryRulesListener;
import com.coaxgames.smp.listener.ReclaimableItemService;
import com.coaxgames.smp.ritual.RitualManager;
import com.coaxgames.smp.storage.LegendaryDataStore;
import com.coaxgames.smp.util.ConfigNumbers;
import com.coaxgames.smp.util.CooldownManager;
import com.coaxgames.smp.util.HeadDropVersion;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class GalaxySmpPlugin extends JavaPlugin {
    private LegendaryItemRegistry legendaryItems;
    private LegendaryDataStore dataStore;
    private CooldownManager cooldownManager;
    private RitualManager ritualManager;
    private PhaseFourDailyEffectManager phaseFourDailyEffects;

    @Override
    public void onEnable() {
        requireHeadDrop();
        saveDefaultConfig();
        validatePlayerItemLimits();
        validateItemNameGradients();
        validateRitualSettings();
        validatePhaseFourSettings();
        validatePhaseFiveMaceSettings();
        LegendaryItemRegistry.disableVanillaMaceRecipe();
        legendaryItems = new LegendaryItemRegistry(this);
        PhaseFourItems.register(this, legendaryItems);
        PhaseFiveMaces.register(this, legendaryItems);
        try {
            dataStore = new LegendaryDataStore(getDataFolder().toPath());
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            getLogger().log(Level.SEVERE, "Could not load plugin data; disabling the plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        new ReclaimableItemService(this).rebuildSpawnBlockMarkers();
        cooldownManager = new CooldownManager(getConfig(), dataStore, legendaryItems);
        ritualManager = new RitualManager(this);
        ritualManager.resume();
        phaseFourDailyEffects = new PhaseFourDailyEffectManager(this);
        try {
            phaseFourDailyEffects.start();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not resume persisted Phase 4 effects; disabling the plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        for (var online : getServer().getOnlinePlayers()) {
            online.discoverRecipes(legendaryItems.getRecipeKeys());
        }
        getLogger().info("Registered " + legendaryItems.getRecipeKeys().size() + " Galaxy SMP recipes.");
        getServer().getPluginManager().registerEvents(new LegendaryCraftListener(this), this);
        getServer().getPluginManager().registerEvents(new LegendaryRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new PhaseFourAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new PhaseFiveMaceAbilityListener(this), this);
        if (getCommand("galaxy") == null) {
            throw new IllegalStateException("The galaxy command is missing from plugin.yml.");
        }
        GalaxyCommand galaxyCommand = new GalaxyCommand(this);
        getCommand("galaxy").setExecutor(galaxyCommand);
        getCommand("galaxy").setTabCompleter(galaxyCommand);
    }

    @Override
    public void onDisable() {
        if (ritualManager != null) {
            ritualManager.shutdown();
        }
        if (phaseFourDailyEffects != null) {
            phaseFourDailyEffects.shutdown();
        }
        if (dataStore != null) {
            try {
                dataStore.pauseActiveEffects(System.currentTimeMillis());
                dataStore.save();
            } catch (IOException exception) {
                getLogger().log(Level.SEVERE, "Could not save plugin data during shutdown.", exception);
            }
        }
    }

    public LegendaryItemRegistry getLegendaryItems() {
        if (legendaryItems == null) {
            throw new IllegalStateException("Legendary item registry is not available before plugin startup.");
        }
        return legendaryItems;
    }

    public LegendaryDataStore getDataStore() {
        if (dataStore == null) {
            throw new IllegalStateException("Persistent data store is not available before plugin startup.");
        }
        return dataStore;
    }

    public CooldownManager getCooldownManager() {
        if (cooldownManager == null) {
            throw new IllegalStateException("Cooldown manager is not available before plugin startup.");
        }
        return cooldownManager;
    }

    public RitualManager getRitualManager() {
        if (ritualManager == null) {
            throw new IllegalStateException("Ritual manager is not available before plugin startup.");
        }
        return ritualManager;
    }

    private void requireHeadDrop() {
        var dependency = getServer().getPluginManager().getPlugin("HeadDrop");
        if (dependency == null) {
            throw new IllegalStateException("Head Drop 3.1.0 or newer is required.");
        }
        String version = dependency.getDescription().getVersion();
        if (!HeadDropVersion.isSupported(version)) {
            throw new IllegalStateException("Head Drop 3.1.0 or newer is required; found " + version + ".");
        }
    }

    private void validatePlayerItemLimits() {
        String defaultPath = "legendary-items.max-per-player.default";
        if (getConfig().contains(defaultPath)
                && (!getConfig().isInt(defaultPath) || getConfig().getInt(defaultPath) < 0)) {
            throw new IllegalStateException(defaultPath + " must be a non-negative integer (0 means unlimited).");
        }
        String itemLimitsPath = "legendary-items.max-per-player.items";
        var itemLimits = getConfig().getConfigurationSection(itemLimitsPath);
        if (itemLimits == null) {
            return;
        }
        for (String itemId : itemLimits.getKeys(false)) {
            String path = itemLimitsPath + "." + itemId;
            if (!getConfig().isInt(path) || getConfig().getInt(path) < 0) {
                throw new IllegalStateException(path + " must be a non-negative integer (0 means unlimited).");
            }
        }
    }

    private void validateItemNameGradients() {
        String path = "legendary-items.display-names";
        var names = getConfig().getConfigurationSection(path);
        if (names == null) {
            return;
        }
        for (String itemId : names.getKeys(false)) {
            String itemPath = path + "." + itemId;
            String name = getConfig().getString(itemPath + ".text");
            String startColor = getConfig().getString(itemPath + ".start-color");
            String endColor = getConfig().getString(itemPath + ".end-color");
            try {
                com.coaxgames.smp.item.LegendaryItemBuilder.gradientComponent(name, startColor, endColor);
            } catch (NullPointerException | IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid configured item name gradient at " + itemPath + ".", exception);
            }
        }
    }

    private void validateRitualSettings() {
        for (String path : java.util.List.of(
                "rituals.duration-seconds",
                "rituals.reveal-interval-seconds")) {
            if (getConfig().contains(path)
                    && (!ConfigNumbers.isWholeNumber(getConfig(), path) || getConfig().getLong(path) <= 0)) {
                throw new IllegalStateException(path + " must be a positive number of seconds.");
            }
        }
    }

    private void validatePhaseFourSettings() {
        for (String path : java.util.List.of(
                "phase-four.combo-window-seconds",
                "phase-four.astral-sword.attack-speed-seconds",
                "phase-four.daily-trigger-tick",
                "phase-four.daily-effect-seconds",
                "phase-four.kuiper-ball.freeze-seconds",
                "phase-four.mass-orb.feather-falling-level",
                "phase-four.mass-orb.passive-refresh-ticks")) {
            if (getConfig().contains(path)
                    && (!ConfigNumbers.isWholeNumber(getConfig(), path) || getConfig().getLong(path) <= 0)) {
                throw new IllegalStateException(path + " must be a positive integer.");
            }

        }
        validateMaximumLong("phase-four.combo-window-seconds", Long.MAX_VALUE / 1_000L);
        validateMaximumLong("phase-four.astral-sword.attack-speed-seconds", Long.MAX_VALUE / 20L);
        validateMaximumLong("phase-four.daily-effect-seconds", Long.MAX_VALUE / 1_000L);
        validateMaximumLong("phase-four.kuiper-ball.freeze-seconds", Integer.MAX_VALUE / 20L);
        validateMaximumLong("phase-four.mass-orb.feather-falling-level", Integer.MAX_VALUE);
        validateMaximumLong("phase-four.mass-orb.passive-refresh-ticks", Integer.MAX_VALUE);
        long triggerTick = getConfig().getLong("phase-four.daily-trigger-tick", 13_500L);
        if (triggerTick >= 24_000L) {
            throw new IllegalStateException("phase-four.daily-trigger-tick must be between 0 and 23999.");
        }
        for (String path : java.util.List.of(
                "phase-four.kuiper-ball.burst-radius",
                "phase-four.kuiper-ball.burst-strength",
                "phase-four.mass-orb.free-fall-distance",
                "phase-four.mass-orb.damage-per-block",
                "phase-four.mass-orb.maximum-damage",
                "phase-four.mass-orb.feather-falling-reduction-per-level",
                "phase-four.mass-orb.maximum-feather-falling-reduction")) {
            if (getConfig().contains(path)) {
                var value = ConfigNumbers.finiteNumber(getConfig(), path);
                if (value.isEmpty() || value.getAsDouble() < 0.0) {
                    throw new IllegalStateException(path + " must be a non-negative number; found "
                            + getConfig().get(path) + ".");
                }
            }
        }
    }

    private void validatePhaseFiveMaceSettings() {
        String capPath = "phase-five.mace-damage-cap-hearts";
        double cap = configuredNumber(capPath, 7.0);
        if (cap <= 0.0) {
            throw new IllegalStateException(capPath + " must be a positive number.");
        }
        for (String itemId : java.util.List.of("star_mace", "collapsing_star_mace")) {
            String cooldownPath = "cooldowns." + itemId;
            if (getConfig().contains(cooldownPath)
                    && (!ConfigNumbers.isWholeNumber(getConfig(), cooldownPath)
                    || getConfig().getLong(cooldownPath) <= 0)) {
                throw new IllegalStateException(cooldownPath + " must be a positive whole number of seconds.");
            }
        }
        String countPath = "phase-five.collapsing-star-mace.spark-count-per-hit";
        int sparkCount = getConfig().getInt(countPath, 1);
        if (sparkCount < 1 || sparkCount > 20
                || !ConfigNumbers.isWholeNumber(getConfig(), countPath)) {
            throw new IllegalStateException(countPath + " must be a whole number from 1 to 20.");
        }
        String radiusPath = "phase-five.collapsing-star-mace.spark-radius";
        double radius = configuredNumber(radiusPath, 1.5);
        if (radius < 0.0) {
            throw new IllegalStateException(radiusPath + " must be a non-negative number.");
        }
        String damagePath = "phase-five.collapsing-star-mace.spark-damage";
        double damage = configuredNumber(damagePath, 2.0);
        if (damage <= 0.0 || damage > 2.0) {
            throw new IllegalStateException(damagePath + " must be greater than zero and at most two health points.");
        }
    }

    private double configuredNumber(String path, double fallback) {
        if (!getConfig().contains(path)) {
            return fallback;
        }
        return ConfigNumbers.finiteNumber(getConfig(), path)
                .orElseThrow(() -> new IllegalStateException(path + " must be a finite number."));
    }

    private void validateMaximumLong(String path, long maximum) {
        if (getConfig().contains(path) && getConfig().getLong(path) > maximum) {
            throw new IllegalStateException(path + " exceeds the supported maximum of " + maximum + ".");
        }
    }
}
