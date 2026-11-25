package com.coaxgames.smp.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.potion.PotionEffectType;

public final class InvisibleNameListener implements Listener {
    private static final String SCRAMBLE_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!isInvisible(player)) {
            return;
        }
        ChatRenderer previous = event.renderer();
        String name = player.getName();
        Component replacement = scrambledName(name);
        event.renderer((source, displayName, message, viewer) ->
                replaceName(previous.render(source, displayName, message, viewer), name, replacement));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        Component message = event.deathMessage();
        if (killer == null || message == null || !isInvisible(killer)) {
            return;
        }
        event.deathMessage(replaceName(message, killer.getName(), scrambledName(killer.getName())));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Component message = event.message();
        if (message == null || !isInvisible(player)) {
            return;
        }
        event.message(replaceName(message, player.getName(), scrambledName(player.getName())));
    }

    static Component replaceName(Component message, String name, Component replacement) {
        if (message == null || name == null || name.isEmpty() || replacement == null) {
            return message;
        }
        return message.replaceText(builder -> builder
                .matchLiteral(name)
                .once()
                .replacement(replacement));
    }

    static Component scrambledName(String realName) {
        return scrambledName(realName, ThreadLocalRandom.current()::nextInt);
    }

    static Component scrambledName(String realName, java.util.function.IntFunction<Integer> randomIndex) {
        String lowerName = realName.toLowerCase(Locale.ROOT);
        String allowed = SCRAMBLE_ALPHABET.chars()
                .mapToObj(character -> String.valueOf((char) character))
                .filter(character -> !lowerName.contains(character.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining());
        if (allowed.isEmpty()) {
            throw new IllegalStateException("No safe scrambled name characters remain.");
        }
        StringBuilder scrambled = new StringBuilder(realName.length());
        for (int i = 0; i < realName.length(); i++) {
            int index = randomIndex.apply(allowed.length());
            if (index < 0 || index >= allowed.length()) {
                throw new IllegalArgumentException("Random index is outside the scramble alphabet.");
            }
            scrambled.append(allowed.charAt(index));
        }
        return Component.text(scrambled.toString()).decorate(TextDecoration.OBFUSCATED);
    }

    private boolean isInvisible(Player player) {
        return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }
}
