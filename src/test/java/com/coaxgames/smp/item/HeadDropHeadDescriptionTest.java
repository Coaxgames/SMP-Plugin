package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

class HeadDropHeadDescriptionTest {
    @Test
    void describesHeadOwnerNameAndUuid() {
        ItemStack head = mock(ItemStack.class);
        SkullMeta meta = mock(SkullMeta.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        UUID uuid = UUID.randomUUID();
        when(head.getType()).thenReturn(Material.PLAYER_HEAD);
        when(head.getItemMeta()).thenReturn(meta);
        when(meta.getOwnerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("LostPlayer");
        when(profile.getUniqueId()).thenReturn(uuid);

        assertEquals("LostPlayer (" + uuid + ")", HeadDropHead.describeOwner(head));
    }

    @Test
    void fallsBackToLegacyOwnerOrUnknown() {
        ItemStack named = mock(ItemStack.class);
        SkullMeta namedMeta = mock(SkullMeta.class);
        when(named.getType()).thenReturn(Material.PLAYER_HEAD);
        when(named.getItemMeta()).thenReturn(namedMeta);
        when(namedMeta.getOwnerProfile()).thenReturn(null);
        when(namedMeta.getOwner()).thenReturn("LegacyName");
        assertEquals("LegacyName", HeadDropHead.describeOwner(named));

        ItemStack unknown = mock(ItemStack.class);
        SkullMeta unknownMeta = mock(SkullMeta.class);
        when(unknown.getType()).thenReturn(Material.PLAYER_HEAD);
        when(unknown.getItemMeta()).thenReturn(unknownMeta);
        assertEquals("unknown", HeadDropHead.describeOwner(unknown));
    }
}
