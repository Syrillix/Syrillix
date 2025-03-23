package atoll.modules.features.mining;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoMithril extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    private Setting.BooleanSetting showMessages;
    private Setting.SliderSetting range;
    private Setting.SliderSetting minMiningDelay;
    private Setting.SliderSetting maxMiningDelay;
    private Setting.BooleanSetting smoothRotation;
    private Setting.BooleanSetting mineStainedClay;
    private Setting.BooleanSetting mineCyanWool;
    private Setting.BooleanSetting minePrismarine;
    private Setting.BooleanSetting mineLightBlueWool;
    private Setting.BooleanSetting debug;

    private BlockPos targetBlock = null;
    private boolean isMining = false;
    private int miningCooldown = 0;
    private boolean isAimingAtTarget = false;

    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0f;
    private float startPitch = 0f;

    private Map<BlockPos, Long> bedrockBlocks = new ConcurrentHashMap<>();
    private List<BlockPos> potentialBlocks = new CopyOnWriteArrayList<>();

    public AutoMithril() {
        super("AutoMithril", Keyboard.KEY_NONE, Category.CategoryType.MINING);

        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.range = new Setting.SliderSetting("Mining Range", 5, 2, 6, 1);
        this.minMiningDelay = new Setting.SliderSetting("Min Mining Delay", 150, 50, 500, 1);
        this.maxMiningDelay = new Setting.SliderSetting("Max Mining Delay", 300, 100, 1000, 1);
        this.smoothRotation = new Setting.BooleanSetting("Smooth Rotation", true);
        this.mineStainedClay = new Setting.BooleanSetting("Mine Cyan Clay", true);
        this.mineCyanWool = new Setting.BooleanSetting("Mine Cyan Wool", true);
        this.minePrismarine = new Setting.BooleanSetting("Mine Prismarine", true);
        this.mineLightBlueWool = new Setting.BooleanSetting("Mine Light Blue Wool", true);
        this.debug = new Setting.BooleanSetting("Debug Mode", false);

        addSetting(showMessages);
        addSetting(range);
        addSetting(minMiningDelay);
        addSetting(maxMiningDelay);
        addSetting(smoothRotation);
        addSetting(mineStainedClay);
        addSetting(mineCyanWool);
        addSetting(minePrismarine);
        addSetting(mineLightBlueWool);
        addSetting(debug);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }
        resetMining();
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }
        resetMining();
        resetRotation();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        if (event.phase != TickEvent.Phase.END) return;

        if (isRotating) {
            updateRotation();
        }

        updateBedrockBlocks();

        if (isMining && targetBlock != null) {
            handleActiveMining();
        } else {
            findNewTarget();
        }
    }

    private void updateBedrockBlocks() {
        Iterator<Map.Entry<BlockPos, Long>> iterator = bedrockBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            long timestamp = entry.getValue();

            if (System.currentTimeMillis() - timestamp > 3000) {
                Block block = mc.theWorld.getBlockState(pos).getBlock();
                if (block != Blocks.bedrock) {
                    iterator.remove();

                    if (isMithrilBlock(pos)) {
                        potentialBlocks.add(pos);
                        if (debug.getValue()) {
                            debugMessage("Block at " + pos + " regenerated from bedrock");
                        }
                    }
                }
            }
        }
    }

    private void handleActiveMining() {
        if (targetBlock == null || !isMithrilBlock(targetBlock)) {
            Block block = mc.theWorld.getBlockState(targetBlock).getBlock();
            if (block == Blocks.bedrock) {
                if (debug.getValue()) {
                    debugMessage("Block turned into bedrock, tracking for regeneration");
                }
                bedrockBlocks.put(targetBlock, System.currentTimeMillis());
            }

            finishMining();
            return;
        }

        if (smoothRotation.getValue()) {
            lookAtBlock(targetBlock);
        } else {
            directLookAtBlock(targetBlock);
        }

        if (isAimingAtTarget) {
            if (miningCooldown <= 0) {
                mineBlock();

                miningCooldown = (int) (minMiningDelay.getValue() +
                        random.nextInt((int) (maxMiningDelay.getValue() - minMiningDelay.getValue() + 1)));

                if (debug.getValue()) {
                    debugMessage("Mining block at " + targetBlock);
                }
            } else {
                miningCooldown--;
            }
        } else {
            if (debug.getValue() && random.nextInt(20) == 0) {
                debugMessage("Still aiming at block...");
            }
        }
    }

    private void findNewTarget() {
        if (mc.theWorld == null) return;

        if (!potentialBlocks.isEmpty()) {
            Iterator<BlockPos> iterator = potentialBlocks.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (isMithrilBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                    targetBlock = pos;
                    iterator.remove();
                    startMining();
                    return;
                } else {
                    iterator.remove();
                }
            }
        }

        int range = (int) Math.ceil(this.range.getValue());
        BlockPos playerPos = mc.thePlayer.getPosition();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (bedrockBlocks.containsKey(pos)) continue;

                    if (isMithrilBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                        targetBlock = pos;
                        startMining();
                        return;
                    }
                }
            }
        }
    }

    private boolean isMithrilBlock(BlockPos pos) {
        if (mc.theWorld == null) return false;

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        int metadata = block.getMetaFromState(state);

        if (mineStainedClay.getValue() && block == Blocks.stained_hardened_clay && metadata == 9) {
            return true;
        }

        if (mineCyanWool.getValue() && block == Blocks.wool && metadata == 9) {
            return true;
        }

        if (mineLightBlueWool.getValue() && block == Blocks.wool && metadata == 3) {
            return true;
        }

        if (minePrismarine.getValue() && (
                block == Blocks.prismarine)) {
            return true;
        }

        return false;
    }

    private boolean isInRange(BlockPos pos) {
        double distSq = mc.thePlayer.getDistanceSq(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5);
        return distSq <= (range.getValue() * range.getValue());
    }

    private boolean canSeeBlock(BlockPos pos) {
        Vec3 blockCenter = new Vec3(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5);

        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);

        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(
                eyePos,
                blockCenter,
                false,
                true,
                false);

        return result == null || pos.equals(result.getBlockPos());
    }

    private void startMining() {
        if (targetBlock == null) return;

        isMining = true;
        isAimingAtTarget = false;
        miningCooldown = 5;

        if (debug.getValue()) {
            debugMessage("Starting to mine block at " + targetBlock);
        }
    }

    private void finishMining() {
        isMining = false;
        isAimingAtTarget = false;
        targetBlock = null;
        resetRotation();

        findNewTarget();
    }

    private void resetMining() {
        targetBlock = null;
        isMining = false;
        isAimingAtTarget = false;
    }

    private void mineBlock() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;

        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos blockPos = mc.objectMouseOver.getBlockPos();
            EnumFacing side = mc.objectMouseOver.sideHit;

            try {
                // Reset click delay
                java.lang.reflect.Field leftClickCounterField = Minecraft.class.getDeclaredField("leftClickCounter");
                leftClickCounterField.setAccessible(true);
                leftClickCounterField.set(mc, 0);

                // Try to start block breaking if not already breaking
                if (!isMiningBlock(blockPos)) {
                    mc.playerController.clickBlock(blockPos, side);
                }

                // Apply multiple damage ticks to speed up mining
                for (int i = 0; i < 5; i++) {
                    mc.playerController.onPlayerDamageBlock(blockPos, side);
                }

                // Swing arm
                mc.thePlayer.swingItem();

                if (debug.getValue()) {
                    debugMessage("Mining with enhanced controller damage");
                }
            } catch (Exception e) {
                if (debug.getValue()) {
                    debugMessage("Controller damage method failed: " + e.getMessage());
                }
            }
        }
    }

    private boolean isMiningBlock(BlockPos pos) {
        try {
            java.lang.reflect.Field currentBlockField = net.minecraft.client.multiplayer.PlayerControllerMP.class.getDeclaredField("currentBlock");
            currentBlockField.setAccessible(true);
            BlockPos currentBlock = (BlockPos) currentBlockField.get(mc.playerController);
            return pos.equals(currentBlock);
        } catch (Exception e) {
            return false;
        }
    }

    private void lookAtBlock(BlockPos pos) {
        if (pos == null) return;

        double targetX = pos.getX() + 0.5;
        double targetY = pos.getY() + 0.5;
        double targetZ = pos.getZ() + 0.5;

        double deltaX = targetX - mc.thePlayer.posX;
        double deltaY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = targetZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        startRotation(yaw, pitch, 80 + random.nextInt(40));
    }

    private void directLookAtBlock(BlockPos pos) {
        if (pos == null) return;

        double targetX = pos.getX() + 0.5;
        double targetY = pos.getY() + 0.5;
        double targetZ = pos.getZ() + 0.5;

        double deltaX = targetX - mc.thePlayer.posX;
        double deltaY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = targetZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;

        isAimingAtTarget = true;
    }

    private void startRotation(float targetYaw, float targetPitch, long duration) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.isRotating = true;
        this.isAimingAtTarget = false;
        this.rotationStartTime = System.currentTimeMillis();
        this.rotationDuration = duration;
        this.startYaw = mc.thePlayer.rotationYaw;
        this.startPitch = mc.thePlayer.rotationPitch;
    }

    private void updateRotation() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - rotationStartTime;

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float pitchDiff = targetPitch - mc.thePlayer.rotationPitch;

        if (Math.abs(yawDiff) < 0.3F && Math.abs(pitchDiff) < 0.3F) {
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            isAimingAtTarget = true;
            return;
        }

        if (elapsedTime >= rotationDuration) {
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            isAimingAtTarget = true;
            return;
        }

        float progress = (float) elapsedTime / rotationDuration;

        progress = easeInOutQuad(progress);

        yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - startYaw);
        pitchDiff = targetPitch - startPitch;

        mc.thePlayer.rotationYaw = startYaw + yawDiff * progress;
        mc.thePlayer.rotationPitch = startPitch + pitchDiff * progress;

        if (progress > 0.9) {
            isAimingAtTarget = true;
        }
    }

    private void resetRotation() {
        isRotating = false;
        isAimingAtTarget = false;
    }

    private float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float)Math.pow(-2 * t + 2, 2) / 2;
    }

    private void debugMessage(String message) {
        if (debug.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll Debug] §7" + message));
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        if (targetBlock != null) {
            renderBlockOutline(targetBlock, Color.GREEN, event.partialTicks);
        }

        for (BlockPos pos : bedrockBlocks.keySet()) {
            renderBlockOutline(pos, Color.RED, event.partialTicks);
        }

        for (BlockPos pos : potentialBlocks) {
            renderBlockOutline(pos, Color.BLUE, event.partialTicks);
        }
    }

    private void renderBlockOutline(BlockPos pos, Color color, float partialTicks) {
    }

    private String getBlockName(BlockPos pos) {
        if (mc.theWorld == null) return "Unknown";

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        int metadata = block.getMetaFromState(state);

        if (block == Blocks.stained_hardened_clay) {
            return "Stained Clay (Meta: " + metadata + ")";
        } else if (block == Blocks.wool) {
            return "Wool (Meta: " + metadata + ")";
        } else if (block == Blocks.prismarine) {
            return "Prismarine";
        } else if (block == Blocks.bedrock) {
            return "Bedrock";
        } else {
            return block.getLocalizedName();
        }
    }
}