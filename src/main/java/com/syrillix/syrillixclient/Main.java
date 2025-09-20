package com.syrillix.syrillixclient;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import com.syrillix.syrillixclient.gui.ClickGUI;

@Mod(modid = SyrillixMod.MODID, name = SyrillixMod.MODNAME, version = SyrillixMod.VERSION)
public class SyrillixMod {
    public static final String MODID = "syrillix";
    public static final String MODNAME = "Syrillix Client";
    public static final String VERSION = "1.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("Syrillix Client init");
        MinecraftForge.EVENT_BUS.register(new ClickGUI());
    }
}