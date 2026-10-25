package com.coaxgames.smp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegendaryDataStoreTest {
    @TempDir
    Path dataDirectory;

    @Test
    void craftRecordSurvivesReloadAndCannotExceedItsLimit() throws Exception {
        LegendaryDataStore firstStore = new LegendaryDataStore(dataDirectory);
        assertTrue(firstStore.tryRecordCraft("astral_sword", 1));
        assertFalse(firstStore.tryRecordCraft("astral_sword", 1));

        LegendaryDataStore reloadedStore = new LegendaryDataStore(dataDirectory);
        assertTrue(reloadedStore.hasBeenCrafted("astral_sword"));
        assertEquals(1, reloadedStore.getCraftCount("astral_sword"));
        assertFalse(reloadedStore.tryRecordCraft("astral_sword", 1));
    }

    @Test
    void administrativeGrantPersistsOwnershipAndLastKnownLocation() throws Exception {
        UUID itemUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        Location location = new Location(null, 12.5, 64, -9.25);

        LegendaryDataStore firstStore = new LegendaryDataStore(dataDirectory);
        firstStore.recordAdministrativeGrant("astral_sword", 1, itemUuid, ownerUuid, location);

        LegendaryDataStore reloadedStore = new LegendaryDataStore(dataDirectory);
        LegendaryDataStore.ItemRecord record = reloadedStore.getTrackedItems("astral_sword").getFirst();
        assertEquals(itemUuid, record.itemUuid());
        assertEquals(ownerUuid, record.ownerUuid());
        assertEquals("astral_sword", record.itemId());
        assertEquals(12.5, record.x());
        assertEquals(64, record.y());
        assertEquals(-9.25, record.z());
        assertTrue(reloadedStore.hasBeenCrafted("astral_sword"));
        assertFalse(reloadedStore.tryRecordCraft("astral_sword", 1));
    }

    @Test
    void cooldownExpirySurvivesReloadAndCanBeClearedWithoutResettingCraftHistory() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        LegendaryDataStore firstStore = new LegendaryDataStore(dataDirectory);
        firstStore.tryRecordCraft("void_sword", 1);
        firstStore.setCooldownExpiry(playerUuid, "void_sword", System.currentTimeMillis() + 30_000);

        LegendaryDataStore reloadedStore = new LegendaryDataStore(dataDirectory);
        assertTrue(reloadedStore.getCooldownExpiry(playerUuid, "void_sword") > System.currentTimeMillis());
        reloadedStore.clearCooldown(playerUuid, "void_sword");

        LegendaryDataStore finalStore = new LegendaryDataStore(dataDirectory);
        assertEquals(0, finalStore.getCooldownExpiry(playerUuid, "void_sword"));
        assertTrue(finalStore.hasBeenCrafted("void_sword"));
    }

    @Test
    void atomicallyRecordsCraftedItemAndAllowsNullOwnerForCrafterAutomation() throws Exception {
        UUID itemUuid = UUID.randomUUID();
        LegendaryDataStore firstStore = new LegendaryDataStore(dataDirectory);

        assertTrue(firstStore.recordCraftedItem(
                "star_mace",
                1,
                itemUuid,
                null,
                new Location(null, 0, 64, 0)
        ));
        assertFalse(firstStore.recordCraftedItem(
                "star_mace",
                1,
                UUID.randomUUID(),
                null,
                new Location(null, 0, 64, 0)
        ));

        LegendaryDataStore reloadedStore = new LegendaryDataStore(dataDirectory);
        assertTrue(reloadedStore.hasBeenCrafted("star_mace"));
        assertEquals(1, reloadedStore.getCraftCount("star_mace"));
        assertEquals(itemUuid, reloadedStore.getTrackedItem(itemUuid).itemUuid());
        assertNull(reloadedStore.getTrackedItem(itemUuid).ownerUuid());
    }

    @Test
    void customMaceCraftLimitsAreIndependentByItemType() throws Exception {
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        assertTrue(store.tryRecordCraft("star_mace", 1));
        assertTrue(store.tryRecordCraft("collapsing_star_mace", 1));
        assertFalse(store.tryRecordCraft("star_mace", 1));
        assertFalse(store.tryRecordCraft("collapsing_star_mace", 1));
    }

    @Test
    void repeatableItemRecordsDoNotConsumeCraftOnceQuota() throws Exception {
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        assertTrue(store.recordCraftedItem(
                "mass_orb",
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Location(null, 0, 64, 0),
                false
        ));
        assertTrue(store.recordCraftedItem(
                "mass_orb",
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Location(null, 0, 64, 0),
                false
        ));
        assertEquals(0, store.getCraftCount("mass_orb"));
    }

    @Test
    void ritualStateAndRevealProgressSurviveReloadUntilDeliveryCompletes() throws Exception {
        UUID itemUuid = UUID.randomUUID();
        UUID crafterUuid = UUID.randomUUID();
        LegendaryDataStore firstStore = new LegendaryDataStore(dataDirectory);

        assertTrue(firstStore.startRitual(
                "astral_sword",
                1,
                itemUuid,
                crafterUuid,
                "CrafterName",
                "world",
                10.5,
                64.0,
                -2.5,
                12.25,
                65.0,
                -4.75,
                1_000L,
                901_000L,
                true
        ));

        LegendaryDataStore afterRestart = new LegendaryDataStore(dataDirectory);
        var ritual = afterRestart.getActiveRituals().getFirst();
        assertEquals(itemUuid, ritual.itemUuid());
        assertEquals(crafterUuid, ritual.crafterUuid());
        assertEquals("CrafterName", ritual.crafterName());
        assertEquals("world", ritual.world());
        assertEquals(10.5, ritual.x());
        assertEquals(12.25, ritual.crafterX());
        assertEquals(901_000L, ritual.completesAtMillis());
        assertEquals(0, ritual.reveals());
        assertEquals(1, afterRestart.getCraftCount("astral_sword"));
        assertTrue(afterRestart.advanceRitualReveal(itemUuid, 0));

        LegendaryDataStore afterReveal = new LegendaryDataStore(dataDirectory);
        assertEquals(1, afterReveal.getActiveRituals().getFirst().reveals());
        afterReveal.finishRitual(itemUuid, "world", 11.5, 65.0, -2.5);

        LegendaryDataStore afterDelivery = new LegendaryDataStore(dataDirectory);
        assertTrue(afterDelivery.getActiveRituals().isEmpty());
        assertEquals(1, afterDelivery.getCraftCount("astral_sword"));
        assertEquals(11.5, afterDelivery.getTrackedItem(itemUuid).x());
        assertEquals("world", afterDelivery.getTrackedItem(itemUuid).world());
    }

    @Test
    void ritualCraftOnceQuotaIsEnforcedAtomically() throws Exception {
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        assertTrue(store.startRitual(
                "void_sword", 1, firstUuid, null, "Crafter",
                "world", 0, 64, 0, 0, 64, 0, 1_000L, 901_000L, true));
        assertFalse(store.startRitual(
                "void_sword", 1, secondUuid, null, "Crafter",
                "world", 0, 64, 0, 0, 64, 0, 2_000L, 902_000L, true));
        assertEquals(1, store.getCraftCount("void_sword"));
        assertEquals(1, store.getActiveRituals().size());
    }

    @Test
    void abortingFailedRitualConsumptionRestoresCraftAllowance() throws Exception {
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        UUID itemUuid = UUID.randomUUID();

        assertTrue(store.startRitual(
                "void_sword", 1, itemUuid, null, "Crafter",
                "world", 0, 64, 0, 0, 64, 0, 1_000L, 901_000L, true));
        assertTrue(store.abortRitual(itemUuid));
        assertEquals(0, store.getCraftCount("void_sword"));
        assertNull(store.getTrackedItem(itemUuid));
        assertTrue(store.getActiveRituals().isEmpty());
        assertTrue(store.startRitual(
                "void_sword", 1, UUID.randomUUID(), null, "Crafter",
                "world", 0, 64, 0, 0, 64, 0, 2_000L, 902_000L, true));
    }

    @Test
    void reclaimMarkerLocationPersistsAndCanBeClearedAfterClaim() throws Exception {
        UUID itemUuid = UUID.randomUUID();
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        store.recordAdministrativeGrant(
                "nebula_spear",
                1,
                itemUuid,
                UUID.randomUUID(),
                new Location(null, 0, 64, 0));
        store.recordReclaimMarker(itemUuid, "world", 0, 128, 0);

        LegendaryDataStore reloadedStore = new LegendaryDataStore(dataDirectory);
        var marker = reloadedStore.getReclaimMarkers().getFirst();
        assertEquals(itemUuid, marker.itemUuid());
        assertEquals("world", marker.world());
        assertEquals(0, marker.x());
        assertEquals(128, marker.y());
        assertEquals(0, marker.z());

        reloadedStore.clearReclaimMarker(itemUuid);
        assertTrue(new LegendaryDataStore(dataDirectory).getReclaimMarkers().isEmpty());
    }

    @Test
    void anItemUuidCannotBeReassignedToAnotherItemType() throws Exception {
        UUID itemUuid = UUID.randomUUID();
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);
        store.recordAdministrativeGrant(
                "astral_sword",
                1,
                itemUuid,
                UUID.randomUUID(),
                new Location(null, 0, 64, 0)
        );

        assertThrows(IllegalStateException.class, () -> store.setItemOwner(
                "void_sword",
                itemUuid,
                UUID.randomUUID()
        ));
    }

    @Test
    void malformedCraftCountsAreReportedInsteadOfTreatedAsUncrafted() throws Exception {
        Files.writeString(dataDirectory.resolve("data.yml"), "crafts:\n  astral_sword: not-a-number\n");
        LegendaryDataStore store = new LegendaryDataStore(dataDirectory);

        assertThrows(IllegalStateException.class, () -> store.getCraftCount("astral_sword"));
    }
}
