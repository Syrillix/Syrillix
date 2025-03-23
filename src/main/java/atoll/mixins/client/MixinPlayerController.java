package atoll.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerController {

    @Shadow private int blockHitDelay;
    @Shadow private BlockPos currentBlock;

    @Inject(method = "onPlayerDamageBlock", at = @At("HEAD"), cancellable = true)
    private void onPlayerDamageBlock(BlockPos p_onPlayerDamageBlock_1_, EnumFacing p_onPlayerDamageBlock_2_, CallbackInfoReturnable<Boolean> cir) {
        // Check if the module is enabled here or implement a way to check
        // This will allow instant breaking when the module is active
        if (shouldEnhanceMining()) {
            blockHitDelay = 0;
        }
    }

    @Inject(method = "clickBlock", at = @At("HEAD"))
    private void onClickBlock(BlockPos p_clickBlock_1_, EnumFacing p_clickBlock_2_, CallbackInfoReturnable<Boolean> cir) {
        // Same check for the module activation
        if (shouldEnhanceMining()) {
            blockHitDelay = 0;
            Minecraft.getMinecraft().thePlayer.swingItem();
        }
    }

    private boolean shouldEnhanceMining() {
        // Implement your check here - can be as simple as a static flag
        // that your AutoMithril module sets when active
        // For example:
        // return AutoMithril.INSTANCE.isEnabled();

        // For testing purposes, you can return true to always enable the enhancement
        return true;
    }
}