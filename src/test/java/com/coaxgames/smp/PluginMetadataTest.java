package com.coaxgames.smp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import com.coaxgames.smp.util.HeadDropVersion;
import com.coaxgames.smp.item.LegendaryItemBuilder;

class PluginMetadataTest {
    @Test
    void requiresTheHeadDropPluginAtRuntime() throws IOException {
        try (var pluginYml = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(pluginYml);
            String descriptor = new String(pluginYml.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.matches("(?s).*\\bdepend:\\s*\\R\\s*-\\s*HeadDrop\\b.*"));
            assertTrue(descriptor.matches("(?s).*\\bapi-version:\\s*['\"]?26\\.3['\"]?.*"));
        }
    }

    @Test
    void acceptsSupportedHeadDropVersionsAndRejectsOlderOrMalformedVersions() {
        assertAll(
                () -> assertTrue(HeadDropVersion.isSupported("3.1.0")),
                () -> assertTrue(HeadDropVersion.isSupported("3.10.0")),
                () -> assertTrue(HeadDropVersion.isSupported("4.0.0")),
                () -> assertTrue(HeadDropVersion.isSupported("3.1.0+build.7")),
                () -> assertFalse(HeadDropVersion.isSupported("3.0.99")),
                () -> assertFalse(HeadDropVersion.isSupported("2.99.99")),
                () -> assertFalse(HeadDropVersion.isSupported("3.1.0-SNAPSHOT")),
                () -> assertFalse(HeadDropVersion.isSupported("not-a-version")),
                () -> assertFalse(HeadDropVersion.isSupported(null))
        );
    }

    @Test
    void configDefinesGradientsForEveryPlannedItem() throws IOException {
        Set<String> itemIds = Set.of(
                "nebula_spear", "star_mace", "collapsing_star_mace", "astral_sword", "void_sword",
                "celestial_bow", "wormhole_trident", "cosmic_crown", "singularity_boots",
                "solstice_chestplate", "lunar_leggings", "nova_shield", "galaxy_string",
                "kuiper_ball", "mass_orb"
        );
        try (var configStream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(configStream);
            var config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(configStream, StandardCharsets.UTF_8));
            var names = config.getConfigurationSection("legendary-items.display-names");
            assertNotNull(names);
            assertEquals(itemIds, names.getKeys(false));
            assertEquals(1, config.getInt("legendary-items.max-per-player.default"));
            for (String itemId : itemIds) {
                String path = "legendary-items.display-names." + itemId;
                LegendaryItemBuilder.gradientComponent(
                        config.getString(path + ".text"),
                        config.getString(path + ".start-color"),
                        config.getString(path + ".end-color"));
            }
        }
    }

    @Test
    void phaseFourConfigPreservesRepeatableStackingAndDocumentsPlaceholders() throws IOException {
        try (var configStream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(configStream);
            var config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(configStream, StandardCharsets.UTF_8));
            assertEquals(0, config.getInt("legendary-items.max-per-player.items.galaxy_string"));
            assertEquals(0, config.getInt("legendary-items.max-per-player.items.kuiper_ball"));
            assertEquals(1, config.getInt("legendary-items.max-per-player.default"));
            assertEquals(13_500L, config.getLong("phase-four.daily-trigger-tick"));
            assertEquals(300L, config.getLong("phase-four.daily-effect-seconds"));
            assertEquals(15, config.getInt("phase-four.mass-orb.feather-falling-level"));
            assertFalse(config.getBoolean("phase-four.lunar-leggings.catch-up-if-offline"));
            assertEquals(1.0, config.getDouble("phase-four.mass-orb.damage-per-block"));
        }
    }
}
