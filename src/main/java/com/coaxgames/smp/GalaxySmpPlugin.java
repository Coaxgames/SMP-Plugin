package com.coaxgames.smp;

import com.coaxgames.smp.command.GalaxyCommand;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import com.coaxgames.smp.listener.LegendaryCraftListener;
import com.coaxgames.smp.listener.LegendaryRulesListener;
import com.coaxgames.smp.storage.LegendaryDataStore;
import com.coaxgames.smp.util.CooldownManager;
import com.coaxgames.smp.util.HeadDropVersion;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class GalaxySmpPlugin extends JavaPlugin {
    private LegendaryItemRegistry legendaryItems;
    private LegendaryDataStore dataStore;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        requireHeadDrop();
        saveDefaultConfig();
        validatePlayerItemLimits();
        validateItemNameGradients();
        LegendaryItemRegistry.disableVanillaMaceRecipe();
        legendaryItems = new LegendaryItemRegistry(this);
        try {
            dataStore = new LegendaryDataStore(getDataFolder().toPath());
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            getLogger().log(Level.SEVERE, "Could not load plugin data; disabling the plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        cooldownManager = new CooldownManager(getConfig(), dataStore, legendaryItems);
        getServer().getPluginManager().registerEvents(new LegendaryCraftListener(this), this);
        getServer().getPluginManager().registerEvents(new LegendaryRulesListener(this), this);
        if (getCommand("galaxy") == null) {
            throw new IllegalStateException("The galaxy command is missing from plugin.yml.");
        }
        GalaxyCommand galaxyCommand = new GalaxyCommand(this);
        getCommand("galaxy").setExecutor(galaxyCommand);
        getCommand("galaxy").setTabCompleter(galaxyCommand);
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            try {
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
}
