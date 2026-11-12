package com.coaxgames.smp.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import com.coaxgames.smp.item.LegendaryItemRegistry;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class KuiperBallBurstTest {
    @Test
    void freezesAllEligibleBurstVictimsButNotShooterOrDisabledCategories() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
            YamlConfiguration config = new YamlConfiguration();
            when(plugin.namespace()).thenReturn("galaxysmp");
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.getLegendaryItems()).thenReturn(mock(LegendaryItemRegistry.class));
            PhaseFourAbilityListener listener = new PhaseFourAbilityListener(plugin);
            World world = mock(World.class);
            Snowball projectile = mock(Snowball.class);
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            Player shooter = mock(Player.class);
            Player direct = mock(Player.class);
            Player nearby = mock(Player.class);
            Mob mob = mock(Mob.class);
            Item item = mock(Item.class);
            ProjectileHitEvent event = mock(ProjectileHitEvent.class);
            when(event.getEntity()).thenReturn(projectile);
            when(event.getHitEntity()).thenReturn(direct);
            when(projectile.getPersistentDataContainer()).thenReturn(data);
            when(data.has(any(), eq(PersistentDataType.BYTE))).thenReturn(true);
            when(projectile.getShooter()).thenReturn(shooter);
            when(projectile.getWorld()).thenReturn(world);
            when(projectile.getLocation()).thenReturn(new Location(world, 0, 0, 0));
            when(direct.getLocation()).thenReturn(new Location(world, 1, 0, 0));
            when(nearby.getLocation()).thenReturn(new Location(world, 2, 0, 0));
            when(nearby.getFreezeTicks()).thenReturn(800);
            when(mob.getLocation()).thenReturn(new Location(world, 0, 0, 1));
            when(item.getLocation()).thenReturn(new Location(world, 0, 0, 2));
            when(world.getNearbyEntities(any(Location.class), eq(4.0), eq(4.0), eq(4.0)))
                    .thenReturn(List.of(projectile, shooter, direct, nearby, mob, item));

            listener.onProjectileHit(event);

            verify(direct).setFreezeTicks(600);
            verify(nearby).setFreezeTicks(800);
            verify(mob).setFreezeTicks(600);
            verify(shooter, never()).setFreezeTicks(org.mockito.ArgumentMatchers.anyInt());
            verify(item, never()).setVelocity(any());

            clearInvocations(direct, nearby, mob);
            config.set("phase-four.kuiper-ball.target-players", false);
            listener.onProjectileHit(event);
            verify(direct, never()).setFreezeTicks(org.mockito.ArgumentMatchers.anyInt());
            verify(nearby, never()).setVelocity(any());
            verify(mob).setFreezeTicks(600);

            clearInvocations(mob, item);
            config.set("phase-four.kuiper-ball.target-mobs", false);
            config.set("phase-four.kuiper-ball.target-items", true);
            listener.onProjectileHit(event);
            verify(mob, never()).setFreezeTicks(org.mockito.ArgumentMatchers.anyInt());
            verify(item).setVelocity(any());

            clearInvocations(mob, item);
            when(data.has(any(), eq(PersistentDataType.BYTE))).thenReturn(false);
            listener.onProjectileHit(event);
            verify(mob, never()).setFreezeTicks(org.mockito.ArgumentMatchers.anyInt());
            verify(item, never()).setVelocity(any());
        }
    }
}
