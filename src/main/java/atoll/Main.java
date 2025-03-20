package atoll;

import atoll.features.ModuleManager;
import atoll.gui.ClickGui;
import atoll.gui.Category;
import atoll.gui.setting.Setting;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    public void registerModule(Module module) {
        // Регистрация модуля в MinecraftForge
        MinecraftForge.EVENT_BUS.register(module);
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

    // Базовый класс Module
    public static abstract class Module {
        private final String name;
        private int keyBind;
        private boolean enabled;
        private final Category category;
        private final List<Setting> settings = new ArrayList<>();

        public Module(String name, int keyBind, Category.CategoryType categoryType) {
            this.name = name;
            this.keyBind = keyBind;
            this.category = Category.getCategory(categoryType);
            this.enabled = false;

            if (this.category != null) {
                this.category.addModule(this);
            }
        }

        public String getName() {
            return name;
        }

        public int getKeyBind() {
            return keyBind;
        }

        public void setKeyBind(int keyBind) {
            this.keyBind = keyBind;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void toggle() {
            enabled = !enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }

        public void onEnable() {}

        public void onDisable() {}

        public Category getCategory() {
            return category;
        }

        public void addSetting(Setting setting) {
            settings.add(setting);
        }

        public List<Setting> getSettings() {
            return settings;
        }
    }
}