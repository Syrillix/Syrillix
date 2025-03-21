package atoll.modules.features.combat;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import atoll.util.robotUtil.RobotUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ZombieKiller extends Module {

    private final Setting.BooleanSetting showMessages;
    private final Setting.BooleanSetting showPath;
    private final Setting.BooleanSetting enableParkour;
    private final Setting.SliderSetting searchRadius;
    private final Setting.SliderSetting attackRange;
    private final Setting.BooleanSetting targetClosest;

    private EntityZombie currentTarget;
    private int retargetCooldown = 0;
    private int stuckCounter = 0;
    private boolean isNavigating = false;

    public ZombieKiller() {
        super("ZombieKiller", Keyboard.KEY_NONE, Category.CategoryType.COMBAT);

        // Add settings
        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.showPath = new Setting.BooleanSetting("Show Path", true);
        this.enableParkour = new Setting.BooleanSetting("Enable Parkour", false);
        this.searchRadius = new Setting.SliderSetting("Search Radius", 30, 5, 100,1);
        this.attackRange = new Setting.SliderSetting("Attack Range", 3, 1, 6,1);
        this.targetClosest = new Setting.BooleanSetting("Target Closest", true);

        // Register settings
        addSetting(showMessages);
        addSetting(showPath);
        addSetting(enableParkour);
        addSetting(searchRadius);
        addSetting(attackRange);
        addSetting(targetClosest);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }

        // Reset state
        currentTarget = null;
        retargetCooldown = 0;
        stuckCounter = 0;
        isNavigating = false;

        // Configure RobotUtil
        RobotUtil.setAttackMode(true);
        RobotUtil.setParkourMode(enableParkour.getValue());
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }

        // Stop robot movement
        RobotUtil.stopMovement();
        RobotUtil.setAttackMode(false);
        currentTarget = null;
        isNavigating = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        // Check if the module is toggled
        if (!this.isEnabled()) return;

        // Check if the player exists
        if (Minecraft.getMinecraft().thePlayer == null || Minecraft.getMinecraft().theWorld == null) return;

        // Update parkour mode setting
        RobotUtil.setParkourMode(enableParkour.getValue());

        // Handle retargeting cooldown
        if (retargetCooldown > 0) {
            retargetCooldown--;
        }

        // Check if current target is still valid
        if (currentTarget != null && (!currentTarget.isEntityAlive() ||
                currentTarget.getDistanceToEntity(Minecraft.getMinecraft().thePlayer) > searchRadius.getValue())) {
            if (showMessages.getValue()) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fTarget lost, searching for new zombie..."));
            }
            currentTarget = null;
            RobotUtil.stopMovement();
            isNavigating = false;
            retargetCooldown = 20; // 1 second cooldown before finding a new target
        }

        // Find a new target if needed
        if (currentTarget == null && retargetCooldown <= 0) {
            findNewTarget();
        }

        // Navigate to target if we have one
        if (currentTarget != null) {
            double distanceToTarget = Minecraft.getMinecraft().thePlayer.getDistanceToEntity(currentTarget);

            // If we're close enough to attack, stop moving and let RobotUtil handle the combat
            if (distanceToTarget <= attackRange.getValue()) {
                if (isNavigating) {
                    RobotUtil.stopMovement();
                    isNavigating = false;
                    if (showMessages.getValue()) {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(
                                new ChatComponentText("§b[Atoll] §fIn attack range, engaging zombie!"));
                    }
                }
            }
            // Otherwise, navigate to the target
            else {
                // Update target position if we're already navigating
                if (isNavigating) {
                    BlockPos targetPos = new BlockPos(currentTarget.posX, currentTarget.posY, currentTarget.posZ);
                    BlockPos currentTargetPos = RobotUtil.getTargetPos();

                    // Only update if the zombie has moved significantly
                    if (currentTargetPos == null ||
                            getDistance(targetPos, currentTargetPos) > 3.0) {
                        RobotUtil.setTargetPos(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                        if (showMessages.getValue()) {
                            Minecraft.getMinecraft().thePlayer.addChatMessage(
                                    new ChatComponentText("§b[Atoll] §fUpdating path to moving zombie..."));
                        }
                    }
                }
                // Start navigation if we're not already
                else {
                    BlockPos targetPos = new BlockPos(currentTarget.posX, currentTarget.posY, currentTarget.posZ);
                    RobotUtil.setTargetPos(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                    isNavigating = true;
                    if (showMessages.getValue()) {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(
                                new ChatComponentText("§b[Atoll] §fNavigating to zombie at " +
                                        targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ()));
                    }
                }
            }

            // Check if we're stuck
            if (isNavigating && !RobotUtil.isMoving()) {
                stuckCounter++;
                if (stuckCounter > 100) { // 5 seconds of being stuck
                    if (showMessages.getValue()) {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(
                                new ChatComponentText("§b[Atoll] §cStuck! §fFinding new target..."));
                    }
                    currentTarget = null;
                    RobotUtil.stopMovement();
                    isNavigating = false;
                    stuckCounter = 0;
                    retargetCooldown = 40; // 2 second cooldown
                }
            } else {
                stuckCounter = 0;
            }
        }

        // Run robot update
        RobotUtil.update();
    }

    private void findNewTarget() {
        List<EntityZombie> zombies = Minecraft.getMinecraft().theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityZombie)
                .map(entity -> (EntityZombie) entity)
                .filter(Entity::isEntityAlive)
                .filter(zombie -> zombie.getDistanceToEntity(Minecraft.getMinecraft().thePlayer) <= searchRadius.getValue())
                .collect(Collectors.toList());

        if (!zombies.isEmpty()) {
            if (targetClosest.getValue()) {
                // Sort by distance
                zombies.sort(Comparator.comparingDouble(
                        zombie -> zombie.getDistanceToEntity(Minecraft.getMinecraft().thePlayer)));
            }

            currentTarget = zombies.get(0);

            if (showMessages.getValue()) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fFound zombie target at distance: " +
                                String.format("%.2f", currentTarget.getDistanceToEntity(Minecraft.getMinecraft().thePlayer))));
            }
        } else if (showMessages.getValue() && Minecraft.getMinecraft().thePlayer.ticksExisted % 100 == 0) {
            // Only show this message occasionally to avoid spam
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §fNo zombies found within " + searchRadius.getValue() + " blocks."));
        }
    }

    private double getDistance(BlockPos pos1, BlockPos pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // Check if the module is toggled
        if (!this.isEnabled() || !showPath.getValue()) return;

        // The RobotUtil already has path rendering functionality
        // We don't need to implement anything here as it will be handled by RobotUtil
    }
}
