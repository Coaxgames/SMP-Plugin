package com.coaxgames.smp.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class LegendaryItemBuilderTest {
    @Test
    void createsGradientNameWithConfiguredEndpointColors() {
        var component = LegendaryItemBuilder.gradientComponent("Star", "#FF0000", "#0000FF");

        assertEquals("Star", PlainTextComponentSerializer.plainText().serialize(component));
        assertEquals(TextColor.fromHexString("#FF0000"), component.children().getFirst().style().color());
        assertEquals(TextColor.fromHexString("#0000FF"), component.children().getLast().style().color());
    }

    @Test
    void rejectsInvalidGradientColorsAndEmptyNames() {
        assertThrows(IllegalArgumentException.class,
                () -> LegendaryItemBuilder.gradientComponent("Star", "red", "#0000FF"));
        assertThrows(IllegalArgumentException.class,
                () -> LegendaryItemBuilder.gradientComponent("", "#FF0000", "#0000FF"));
    }
}
