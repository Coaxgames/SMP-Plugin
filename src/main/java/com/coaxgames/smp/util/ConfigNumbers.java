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

    public static java.util.OptionalDouble finiteNumber(ConfigurationSection config, String path) {
        Object value = config.get(path);
        double number;
        if (value instanceof Number numeric) {
            number = numeric.doubleValue();
        } else if (value instanceof String text) {
            try {
                number = Double.parseDouble(text.trim());
            } catch (NumberFormatException exception) {
                return java.util.OptionalDouble.empty();
            }
        } else {
            return java.util.OptionalDouble.empty();
        }
        return Double.isFinite(number) ? java.util.OptionalDouble.of(number) : java.util.OptionalDouble.empty();
    }
}
