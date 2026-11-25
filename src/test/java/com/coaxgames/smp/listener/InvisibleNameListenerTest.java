package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class InvisibleNameListenerTest {
    @Test
    void scrambledNameUsesVanillaObfuscationWithoutRealNameLetters() {
        String realName = "Coaxgames";
        Component scrambled = InvisibleNameListener.scrambledName(realName, bound -> 0);
        String text = PlainTextComponentSerializer.plainText().serialize(scrambled);

        assertEquals(realName.length(), text.length());
        assertTrue(scrambled.hasDecoration(TextDecoration.OBFUSCATED));
        for (char character : realName.toLowerCase(Locale.ROOT).toCharArray()) {
            assertFalse(text.toLowerCase(Locale.ROOT).contains(String.valueOf(character)));
        }
    }

    @Test
    void replacesOnlyOneNameOccurrence() {
        Component replaced = InvisibleNameListener.replaceName(
                Component.text("Coaxgames killed Steve; Coaxgames"),
                "Coaxgames",
                Component.text("XXXXXXXXX"));

        assertEquals(
                "XXXXXXXXX killed Steve; Coaxgames",
                PlainTextComponentSerializer.plainText().serialize(replaced));
    }
}
