package com.mousexbuttonfix;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Fixes LWJGL reporting a press of the first side mouse button (Mouse4) as the second
 * one (Mouse5) while Shift, Ctrl, Alt or another mouse button is held.
 *
 * <p>Client only: the bug is in LWJGL's window message handling and has nothing to do
 * with the server.</p>
 */
@Mod(modid = MouseXButtonFix.MODID, name = MouseXButtonFix.NAME, version = MouseXButtonFix.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9,1.13)")
public class MouseXButtonFix {

    public static final String MODID = "mousexbuttonfix";
    public static final String NAME = "Mouse XButton Fix";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!FMLCommonHandler.instance().getSide().isClient()) {
            return;
        }
        MouseFixInstaller.tryInstall("initialization");
        MinecraftForge.EVENT_BUS.register(new ClientTickHandler());
    }
}
