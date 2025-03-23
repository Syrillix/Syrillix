package atoll;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

@Mod(modid = "atoll", name = "Atoll", version = "1.0")
public class MixinLoader {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.atoll.json");
        MixinEnvironment.getCurrentEnvironment().setObfuscationContext("searge");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Additional initialization if needed
    }
}