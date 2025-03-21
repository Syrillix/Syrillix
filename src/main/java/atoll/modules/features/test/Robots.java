package atoll.modules.features.test;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import atoll.util.robotUtil.RobotUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class Robots extends Module {

    private Setting.BooleanSetting showText;
    private Setting.BooleanSetting enableAttack;
    private Setting.BooleanSetting startNavigation;
    private Setting.BooleanSetting showPath;

    private boolean isNavigating = false;

    public Robots() {
        super("Robots", Keyboard.KEY_NONE, Category.CategoryType.TEST);

        // Add settings
        this.showText = new Setting.BooleanSetting("Show Messages", true);
        this.enableAttack = new Setting.BooleanSetting("Attack Mobs", false);
        this.startNavigation = new Setting.BooleanSetting("Start Navigation", false);
        this.showPath = new Setting.BooleanSetting("Show Path", true);

        // Register settings
        addSetting(showText);
        addSetting(enableAttack);
        addSetting(startNavigation);
        addSetting(showPath);
    }

    @Override
    public void onEnable() {
        if (showText.getValue() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }

        // Reset navigation flag
        isNavigating = false;
        startNavigation.setValue(false);
    }

    @Override
    public void onDisable() {
        if (showText.getValue() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }

        // Stop robot movement
        RobotUtil.stopMovement();
        RobotUtil.setAttackMode(false);
        isNavigating = false;
        startNavigation.setValue(false);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        // Check if the module is toggled
        if (!this.isEnabled()) return;

        // Check if the player exists
        if (Minecraft.getMinecraft().thePlayer == null) return;

        // Check if we need to start navigation
        if (startNavigation.getValue() && !isNavigating) {
            isNavigating = true;
            RobotUtil.setTargetPos(-142, 73, -22);

            if (showText.getValue()) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fStarting navigation to §aCoordinate"));
            }
        } else if (!startNavigation.getValue() && isNavigating) {
            isNavigating = false;
            RobotUtil.stopMovement();

            if (showText.getValue()) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fNavigation stopped"));
            }
        }

        // Update attack mode
        RobotUtil.setAttackMode(enableAttack.getValue());

        // Run robot update
        RobotUtil.update();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // Check if the module is toggled
        if (!this.isEnabled()) return;

        if (!showPath.getValue() || Minecraft.getMinecraft().thePlayer == null) return;

        // TODO: Implement path rendering if needed
        // This would render the current path that the robot is following
    }
}
