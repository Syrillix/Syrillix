package atoll.mixins.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "clickMouse", at = @At("HEAD"))
    private void onClickMouse(CallbackInfo ci) {
        // Reset leftClickCounter to zero to allow rapid breaking
        try {
            java.lang.reflect.Field leftClickCounterField = Minecraft.class.getDeclaredField("leftClickCounter");
            leftClickCounterField.setAccessible(true);
            leftClickCounterField.set(Minecraft.getMinecraft(), 0);
        } catch (Exception ignored) {}
    }
}