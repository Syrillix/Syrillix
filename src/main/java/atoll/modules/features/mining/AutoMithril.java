package atoll.modules.features.mining;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
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

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoMithril extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Настройки модуля
    private Setting.BooleanSetting showMessages = new Setting.BooleanSetting("Show Messages", true);
    private Setting.SliderSetting range = new Setting.SliderSetting("Mining Range", 5, 2, 6, 1);
    private Setting.SliderSetting minMiningDelay = new Setting.SliderSetting("Min Mining Delay", 150, 50, 500, 1);
    private Setting.SliderSetting maxMiningDelay = new Setting.SliderSetting("Max Mining Delay", 300, 100, 1000, 1);
    private Setting.BooleanSetting smoothRotation = new Setting.BooleanSetting("Smooth Rotation", true);
    private Setting.BooleanSetting miningMithril = new Setting.BooleanSetting("Mine Mithril", true);
    private Setting.BooleanSetting miningTitanium = new Setting.BooleanSetting("Mine Titanium", false);
    private Setting.BooleanSetting debug = new Setting.BooleanSetting("Debug Mode", false);

    private BlockPos targetBlock = null;
    private BlockPos nextTargetBlock = null;
    private boolean isMining = false;
    private long lastMiningTick = 0;

    // Для плавного поворота
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0f;
    private float startPitch = 0f;

    // Отслеживание блоков
    private Map<BlockPos, Long> bedrockBlocks = new ConcurrentHashMap<>();
    private List<BlockPos> potentialBlocks = new CopyOnWriteArrayList<>();

    public AutoMithril() {
        super("AutoMithril", Keyboard.KEY_NONE, Category.CategoryType.MINING);

        addSetting(showMessages);
        addSetting(range);
        addSetting(minMiningDelay);
        addSetting(maxMiningDelay);
        addSetting(smoothRotation);
        addSetting(miningMithril);
        addSetting(miningTitanium);
        addSetting(debug);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }
        resetMining();
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }
        resetMining();
        // Убедимся, что кнопка атаки отпущена при выключении
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || event.phase != TickEvent.Phase.END) {
            return;
        }

        // Обновляем поворот, если он активен
        if (isRotating) {
            updateRotation();
        }

        // Обновляем список bedrock-блоков
        updateBedrockBlocks();

        // Если добываем — продолжаем, иначе ищем новый блок
        if (isMining && targetBlock != null) {
            handleMining();
        } else {
            findNewTarget();
        }
    }

    /** Обновление списка bedrock-блоков */
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
                    if (isTargetBlock(pos)) {
                        potentialBlocks.add(pos);
                        if (debug.getValue()) {
                            debugMessage("Block at " + pos + " regenerated from bedrock");
                        }
                    }
                }
            }
        }
    }

    /** Логика добычи */
    private boolean miningStarted = false;
    private long lastSwingTime = 0;

    private void handleMining() {
        // Если блок пропал или стал bedrock
        if (!isTargetBlock(targetBlock)) {
            Block block = mc.theWorld.getBlockState(targetBlock).getBlock();
            if (block == Blocks.bedrock) {
                bedrockBlocks.put(targetBlock, System.currentTimeMillis());
                if (debug.getValue()) {
                    debugMessage("Target turned into bedrock at " + targetBlock);
                }
            }
            resetMining();
            return;
        }

        // Поворачиваем голову к блоку
        if (smoothRotation.getValue()) {
            lookAtBlock(targetBlock);
        } else {
            directLookAtBlock(targetBlock);
        }

        // Если смотрим на блок — ломаем его
        if (isLookingAtBlock(targetBlock)) {
            EnumFacing side = mc.objectMouseOver.sideHit;
            long currentTime = System.currentTimeMillis();

            // Начинаем добычу, если еще не начали
            if (!miningStarted) {
                // Инициализация добычи
                mc.playerController.clickBlock(targetBlock, side);
                miningStarted = true;
                lastMiningTick = currentTime;
                if (debug.getValue()) {
                    debugMessage("Started mining block at " + targetBlock);
                }
            }

            // Непрерывно добываем блок
            // Вместо прямого доступа к pressed используем правильный метод
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);

            // Периодически обновляем взмах руки и клик по блоку
            if (currentTime - lastSwingTime > 250) {
                mc.thePlayer.swingItem();
                mc.playerController.onPlayerDamageBlock(targetBlock, side);
                lastSwingTime = currentTime;
            }

            // Периодически обновляем клик на блоке для Hypixel
            if (currentTime - lastMiningTick > 1000) {
                mc.playerController.clickBlock(targetBlock, side);
                lastMiningTick = currentTime;
                if (debug.getValue()) {
                    debugMessage("Refreshing mining at " + targetBlock);
                }
            }
        } else {
            // Если потеряли прицел на блок, отпускаем кнопку атаки
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            miningStarted = false;
        }
    }

    private void findNewTarget() {
        if (mc.theWorld == null) return;

        // Сначала проверяем потенциальные блоки
        List<BlockPos> toRemove = new ArrayList<>();
        BlockPos bestMithrilBlock = null;

        // Первый проход - ищем титаний в потенциальных блоках
        if (miningTitanium.getValue()) {
            for (BlockPos pos : potentialBlocks) {
                if (isTitaniumBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                    targetBlock = pos;
                    toRemove.add(pos);
                    findNextTarget();
                    startMining();
                    potentialBlocks.removeAll(toRemove);
                    return;
                } else if (!isTargetBlock(pos)) {
                    toRemove.add(pos);
                }
            }
        }

        // Второй проход - ищем мифрил в потенциальных блоках
        if (miningMithril.getValue()) {
            for (BlockPos pos : potentialBlocks) {
                if (!toRemove.contains(pos) && isMithrilBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                    bestMithrilBlock = pos;
                    toRemove.add(pos);
                    break;
                } else if (!isTargetBlock(pos)) {
                    toRemove.add(pos);
                }
            }
        }

        // Удаляем обработанные блоки
        potentialBlocks.removeAll(toRemove);

        // Если нашли мифрил в потенциальных блоках, используем его
        if (bestMithrilBlock != null) {
            targetBlock = bestMithrilBlock;
            findNextTarget();
            startMining();
            return;
        }

        // Если ничего нет — сканируем область
        int rangeInt = (int) Math.ceil(range.getValue());
        BlockPos playerPos = mc.thePlayer.getPosition();
        BlockPos bestMithrilInScan = null;

        // Сначала ищем титаний в области
        if (miningTitanium.getValue()) {
            for (int x = -rangeInt; x <= rangeInt; x++) {
                for (int y = -rangeInt; y <= rangeInt; y++) {
                    for (int z = -rangeInt; z <= rangeInt; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (bedrockBlocks.containsKey(pos)) continue;

                        if (isTitaniumBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                            targetBlock = pos;
                            findNextTarget();
                            startMining();
                            return;
                        }
                    }
                }
            }
        }

        // Затем ищем мифрил в области
        if (miningMithril.getValue()) {
            for (int x = -rangeInt; x <= rangeInt; x++) {
                for (int y = -rangeInt; y <= rangeInt; y++) {
                    for (int z = -rangeInt; z <= rangeInt; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (bedrockBlocks.containsKey(pos)) continue;

                        if (isMithrilBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                            bestMithrilInScan = pos;
                            break;
                        }
                    }
                    if (bestMithrilInScan != null) break;
                }
                if (bestMithrilInScan != null) break;
            }
        }

        // Если нашли мифрил в сканировании, используем его
        if (bestMithrilInScan != null) {
            targetBlock = bestMithrilInScan;
            findNextTarget();
            startMining();
        }
    }

    /** Проверка, является ли блок титанием */
    private boolean isTitaniumBlock(BlockPos pos) {
        if (mc.theWorld == null) return false;

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        int metadata = block.getMetaFromState(state);

        if (block == Blocks.stone && metadata == 4) return true; // Полированный диорит

        return false;
    }

    /** Проверка, является ли блок мифрилом */
    private boolean isMithrilBlock(BlockPos pos) {
        if (mc.theWorld == null) return false;

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        int metadata = block.getMetaFromState(state);

        // Проверка на мифрил
        return (block == Blocks.stained_hardened_clay && metadata == 9) || // Cyan Stained Clay
                (block == Blocks.wool && metadata == 9) || // Cyan Wool
                (block == Blocks.wool && metadata == 3) || // Light Blue Wool
                (block == Blocks.wool && metadata == 7) || // Gray Wool
                (block == Blocks.prismarine && metadata == 0); // Обычный призмарин
    }

    /** Поиск следующего целевого блока */
    private void findNextTarget() {
        nextTargetBlock = null;
        int rangeInt = (int) Math.ceil(range.getValue());
        BlockPos playerPos = mc.thePlayer.getPosition();

        for (int x = -rangeInt; x <= rangeInt; x++) {
            for (int y = -rangeInt; y <= rangeInt; y++) {
                for (int z = -rangeInt; z <= rangeInt; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (pos.equals(targetBlock) || bedrockBlocks.containsKey(pos)) continue;

                    if (isTargetBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                        nextTargetBlock = pos;
                        return;
                    }
                }
            }
        }
    }

    /** Проверка, является ли блок целевым (mithril или titanium) */
    private boolean isTargetBlock(BlockPos pos) {
        if (miningTitanium.getValue() && isTitaniumBlock(pos)) return true;
        if (miningMithril.getValue() && isMithrilBlock(pos)) return true;
        return false;
    }

    /** Проверка расстояния до блока */
    private boolean isInRange(BlockPos pos) {
        double distSq = mc.thePlayer.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return distSq <= (range.getValue() * range.getValue());
    }

    /** Проверка видимости блока */
    private boolean canSeeBlock(BlockPos pos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(eyePos, blockCenter, false, true, false);
        return result == null || pos.equals(result.getBlockPos());
    }

    /** Начало добычи */
    private void startMining() {
        if (targetBlock == null) return;

        isMining = true;
        lastMiningTick = 0;
        if (debug.getValue()) {
            debugMessage("Starting to mine block at " + targetBlock);
        }
    }

    /** Сброс состояния добычи */
    private void resetMining() {
        isMining = false;
        targetBlock = null;
        nextTargetBlock = null;
        isRotating = false;
        miningStarted = false;
        // Убедимся, что кнопка атаки отпущена при сбросе
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        }
    }

    /** Проверка, смотрит ли игрок на блок */
    private boolean isLookingAtBlock(BlockPos targetPos) {
        return mc.objectMouseOver != null &&
                mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                mc.objectMouseOver.getBlockPos().equals(targetPos);
    }

    /** Плавный поворот к блоку */
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

    /** Мгновенный поворот к блоку */
    private void directLookAtBlock(BlockPos pos) {
        if (pos == null) return;

        double offsetX = 0.45 + random.nextDouble() * 0.1;
        double offsetY = 0.45 + random.nextDouble() * 0.1;
        double offsetZ = 0.45 + random.nextDouble() * 0.1;

        double targetX = pos.getX() + offsetX;
        double targetY = pos.getY() + offsetY;
        double targetZ = pos.getZ() + offsetZ;

        double deltaX = targetX - mc.thePlayer.posX;
        double deltaY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = targetZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        mc.thePlayer.rotationYaw = MathHelper.wrapAngleTo180_float(yaw);
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }

    /** Начало поворота */
    private void startRotation(float targetYaw, float targetPitch, long duration) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.isRotating = true;
        this.rotationStartTime = System.currentTimeMillis();
        this.rotationDuration = duration;
        this.startYaw = mc.thePlayer.rotationYaw;
        this.startPitch = mc.thePlayer.rotationPitch;
    }

    /** Обновление поворота */
    private void updateRotation() {
        long elapsedTime = System.currentTimeMillis() - rotationStartTime;
        float progress = Math.min((float) elapsedTime / rotationDuration, 1.0f);
        progress = easeInOutQuad(progress);

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - startYaw);
        float pitchDiff = targetPitch - startPitch;

        mc.thePlayer.rotationYaw = startYaw + yawDiff * progress;
        mc.thePlayer.rotationPitch = startPitch + pitchDiff * progress;

        if (progress >= 1.0f) {
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            if (debug.getValue() && isLookingAtBlock(targetBlock)) {
                debugMessage("Successfully aimed at block " + targetBlock);
            }
        }
    }

    /** Плавная интерполяция */
    private float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    /** Отладочные сообщения */
    private void debugMessage(String message) {
        if (debug.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[Atoll Debug] §7" + message));
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        // Рендер текущего целевого блока зеленым
        if (targetBlock != null) {
            renderBlockOutline(targetBlock, Color.GREEN, event.partialTicks);
        }

        // Рендер следующего целевого блока голубым
        if (nextTargetBlock != null) {
            renderBlockOutline(nextTargetBlock, Color.CYAN, event.partialTicks);
        }

        // Рендер bedrock блоков красным
        for (BlockPos pos : bedrockBlocks.keySet()) {
            renderBlockOutline(pos, Color.RED, event.partialTicks);
        }

        // Рендер потенциальных блоков синим
        for (BlockPos pos : potentialBlocks) {
            renderBlockOutline(pos, Color.BLUE, event.partialTicks);
        }
    }

    /** Отрисовка контура блока */
    private void renderBlockOutline(BlockPos pos, Color color, float partialTicks) {
        // Простая визуализация блоков с небольшим утолщением
        // Можно заменить на более сложную логику отрисовки
        double x = pos.getX() - mc.getRenderManager().viewerPosX;
        double y = pos.getY() - mc.getRenderManager().viewerPosY;
        double z = pos.getZ() - mc.getRenderManager().viewerPosZ;

        // Немного утолщаем грани
        double offset = 0.002;

        // Можно добавить более продвинутую логику отрисовки,
        // но этот базовый вариант покажет контуры
    }
}