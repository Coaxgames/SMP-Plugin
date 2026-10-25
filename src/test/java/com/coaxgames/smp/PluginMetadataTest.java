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
    void bundledConfigWholeNumberSettingsPassStartupValidation() throws Exception {
        var config = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        for (String path : java.util.List.of(
                "rituals.duration-seconds",
                "rituals.reveal-interval-seconds",
                "phase-four.combo-window-seconds",
                "phase-four.astral-sword.attack-speed-seconds",
                "phase-four.daily-trigger-tick",
                "phase-four.daily-effect-seconds",
                "phase-four.kuiper-ball.freeze-seconds",
                "phase-four.mass-orb.feather-falling-level",
                "phase-four.mass-orb.passive-refresh-ticks")) {
            assertTrue(com.coaxgames.smp.util.ConfigNumbers.isWholeNumber(config, path), path);
        }
        assertFalse(com.coaxgames.smp.util.ConfigNumbers.isWholeNumber(config, "phase-four.kuiper-ball.burst-radius"));
        for (String path : java.util.List.of(
                "phase-four.kuiper-ball.burst-radius",
                "phase-four.kuiper-ball.burst-strength",
                "phase-four.mass-orb.free-fall-distance",
                "phase-four.mass-orb.damage-per-block",
                "phase-four.mass-orb.maximum-damage",
                "phase-four.mass-orb.feather-falling-reduction-per-level",
                "phase-four.mass-orb.maximum-feather-falling-reduction")) {
            assertTrue(com.coaxgames.smp.util.ConfigNumbers.finiteNumber(config, path).orElse(-1.0) >= 0.0, path);
        }
        var edited = new YamlConfiguration();
        edited.set("a", 4);
        edited.set("b", "2.5");
        edited.set("c", "nope");
        assertEquals(4.0, com.coaxgames.smp.util.ConfigNumbers.finiteNumber(edited, "a").orElse(-1.0));
        assertEquals(2.5, com.coaxgames.smp.util.ConfigNumbers.finiteNumber(edited, "b").orElse(-1.0));
        assertTrue(com.coaxgames.smp.util.ConfigNumbers.finiteNumber(edited, "c").isEmpty());
    }

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
