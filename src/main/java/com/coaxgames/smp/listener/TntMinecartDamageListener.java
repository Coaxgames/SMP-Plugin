package com.coaxgames.smp.listener;

import com.coaxgames.smp.util.DamageCap;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class TntMinecartDamageListener implements Listener {
    static final double MAX_DAMAGE = 14.0;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof ExplosiveMinecart)
                || event.getFinalDamage() <= MAX_DAMAGE) {
            return;
        }
        double cappedBase = cappedBaseDamage(event.getDamage(), event.getFinalDamage(), MAX_DAMAGE);
        event.setDamage(cappedBase);
    }

    static double cappedBaseDamage(double baseDamage, double finalDamage, double maximumFinalDamage) {
        if (finalDamage <= maximumFinalDamage) {
            return baseDamage;
        }
        double finalScale = finalDamage > 0.0 ? maximumFinalDamage / finalDamage : 0.0;
        return DamageCap.capFinalDamage(baseDamage * finalScale, baseDamage);
    }
}
