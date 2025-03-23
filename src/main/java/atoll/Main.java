package atoll;

import atoll.modules.ModuleManager;
import atoll.gui.ClickGui;
import atoll.gui.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

@Mod(modid = Main.MODID, name = Main.NAME, version = Main.VERSION, clientSideOnly = true)
public class Main {
    public static final String MODID = "atollscript";
    public static final String NAME = "Atoll script";
    public static final String VERSION = "1.0";

    public static Main instance;
    private ClickGui clickGui;
    private ModuleManager moduleManager;
    private boolean shouldOpenGui = false;
    

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        instance = this;
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.atoll.json");
        MixinEnvironment.getCurrentEnvironment().setObfuscationContext("searge");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Регистрация обработчика событий
        MinecraftForge.EVENT_BUS.register(this);

        // Инициализация ClickGui
        clickGui = new ClickGui();

        // Инициализация категорий
        Category.initializeCategories();

        // Добавление категорий в ClickGui
        for (Category category : Category.getCategories()) {
            clickGui.addCategory(category);
        }

        // Инициализация менеджера модулей
        moduleManager = new ModuleManager();
        moduleManager.initializeModules();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && shouldOpenGui) {
            shouldOpenGui = false;
            // In 1.8.9, we need to use FMLClientHandler or direct field access
            Minecraft mc = FMLClientHandler.instance().getClient();
            mc.displayGuiScreen(clickGui);
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        // Only process key presses, not releases
        if (Keyboard.getEventKeyState()) {
            int keyCode = Keyboard.getEventKey();

            // Check for GUI toggle key
            if (keyCode == Keyboard.KEY_RSHIFT) {
                System.out.println("Right shift pressed, opening GUI");
                shouldOpenGui = true;
            }

            // Check module keybinds
            if (moduleManager != null) {
                moduleManager.getModules().forEach(module -> {
                    if (module.getKeyBind() != Keyboard.KEY_NONE && keyCode == module.getKeyBind()) {
                        module.toggle();
                        System.out.println("Toggled module: " + module.getName() + " - Enabled: " + module.isEnabled());
                    }
                });
            }
        }
    }


    public ClickGui getClickGui() {
        return clickGui;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static Main getInstance() {
        return instance;
    }
}