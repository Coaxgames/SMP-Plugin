package com.coaxgames.smp.util;

import org.bukkit.configuration.ConfigurationSection;

public final class ConfigNumbers {
    private ConfigNumbers() {
    }

    // YamlConfiguration#isLong is false for ordinary YAML integers (they load as Integer).
    public static boolean isWholeNumber(ConfigurationSection config, String path) {
        Object value = config.get(path);
        return value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
    }
}
