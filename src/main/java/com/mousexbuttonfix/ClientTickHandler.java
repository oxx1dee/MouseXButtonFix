package com.mousexbuttonfix;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Keeps the fix installed. The mouse does not exist yet when the mod is initialised and
 * LWJGL creates a fresh implementation if the display is recreated, so installing once
 * is not enough.
 */
final class ClientTickHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MouseFixInstaller.tick();
    }
}
