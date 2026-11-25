package com.coaxgames.smp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TntMinecartDamageListenerTest {
    @Test
    void scalesBaseDamageSoFinalDamageCannotExceedSevenHearts() {
        assertEquals(14.0, TntMinecartDamageListener.cappedBaseDamage(28.0, 28.0, 14.0));
        assertEquals(10.0, TntMinecartDamageListener.cappedBaseDamage(20.0, 40.0, 20.0));
    }

    @Test
    void leavesDamageAlreadyWithinTheCapUnchanged() {
        assertEquals(12.0, TntMinecartDamageListener.cappedBaseDamage(12.0, 6.0, 14.0));
    }
}
