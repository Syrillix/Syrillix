package com.syrillix.syrillixclient.gui;

import com.syrillix.syrillixclient.gui.components.GuiCheckbox;
import com.syrillix.syrillixclient.gui.components.GuiSelection;
import com.syrillix.syrillixclient.gui.components.GuiSlider;
import com.syrillix.syrillixclient.gui.components.IconButton;
import com.syrillix.syrillixclient.modules.ModuleManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@SideOnly(Side.CLIENT)
public class ClickGui extends GuiScreen {
    private String currentCategory = "Example";
    private int selectedCategoryIndex = -1;
    private float scrollOffset = 0;
    private float scrollVelocity = 0;
    private List<GuiButton> categoryButtons = new ArrayList<>();
    private List<GuiButton> contentButtons = new ArrayList<>();
    private Properties config = new Properties();

    private final String[] categoryNames = {"Combat", "Fishing", "Garden", "Mining", "Visual", "Macro", "Test"};
    private final ResourceLocation[] categoryIcons = {
            new ResourceLocation("syrillix:textures/gui/icons/combat.png"),
            new ResourceLocation("syrillix:textures/gui/icons/fishing.png"),
            new ResourceLocation("syrillix:textures/gui/icons/garden.png"),
            new ResourceLocation("syrillix:textures/gui/icons/mining.png"),
            new ResourceLocation("syrillix:textures/gui/icons/visual.png"),
            new ResourceLocation("syrillix:textures/gui/icons/macro.png"),
            new ResourceLocation("syrillix:textures/gui/icons/test.png")
    };

    @Override
    public void initGui() {
        super.initGui();
        try {
            config.load(new FileInputStream(new File(mc.mcDataDir, "config/syrillix.cfg")));
        } catch (Exception e) {
            // Игнорируем, если файла нет
        }

        categoryButtons.clear();
        contentButtons.clear();

        int iconY = 50;
        for (int i = 0; i < categoryIcons.length; i++) {
            categoryButtons.add(new IconButton(i, 10, iconY, 32, 32, categoryIcons[i]));
            iconY += 40;
        }

        updateContent();
    }

    private void updateContent() {
        contentButtons.clear();
        if (selectedCategoryIndex == -1) return;

        int y = 50 + (int)scrollOffset;
        String cat = categoryNames[selectedCategoryIndex];

        switch (selectedCategoryIndex) {
            case 0: // Combat
                addModuleToggle(y, "KillAura", cat);
                y += 30;
                addSlider(y, "Range", 0, 10, 5, cat);
                y += 30;
                addCheckbox(y, "AutoAttack", true, cat);
                y += 30;
                addSelection(y, "Mode", Arrays.asList("Single", "Multi"), 0, cat);
                break;
            // Другие категории можно добавить аналогично
        }
    }

    private void addModuleToggle(int y, String moduleName, String cat) {
        String displayName = config.getProperty(cat + "." + moduleName + "Name", moduleName);
        GuiButton btn = new GuiButton(100 + contentButtons.size(), width / 2 - 100, y, 200, 20, displayName + ": " + (getModuleState(moduleName) ? "ON" : "OFF"));
        contentButtons.add(btn);
    }

    private void addSlider(int y, String key, float min, float max, float initial, String cat) {
        String displayName = config.getProperty(cat + "." + key + "Name", key);
        GuiSlider slider = new GuiSlider(200 + contentButtons.size(), width / 2 - 100, y, 200, 20, displayName, min, max, initial);
        contentButtons.add(slider);
    }

    private void addCheckbox(int y, String key, boolean initial, String cat) {
        String displayName = config.getProperty(cat + "." + key + "Name", key);
        GuiCheckbox cb = new GuiCheckbox(300 + contentButtons.size(), width / 2 - 100, y, 20, 20, displayName, initial);
        contentButtons.add(cb);
    }

    private void addSelection(int y, String key, List<String> options, int initial, String cat) {
        String displayName = config.getProperty(cat + "." + key + "Name", key);
        GuiSelection sel = new GuiSelection(400 + contentButtons.size(), width / 2 - 100, y, 200, 20, displayName, options, initial);
        contentButtons.add(sel);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Полупрозрачный фон
        drawDefaultBackground();
        drawRect(0, 0, width, height, 0x80000000); // Полупрозрачный чёрный фон

        // Заголовок
        fontRendererObj.drawStringWithShadow(currentCategory, width / 2 - fontRendererObj.getStringWidth(currentCategory) / 2, 20, 0xFFFFFF);

        // Левая панель с иконками
        drawRect(0, 0, 50, height, 0xAA000000); // Полупрозрачный фон для иконок
        for (GuiButton btn : categoryButtons) {
            btn.drawButton(mc, mouseX, mouseY);
        }

        // Правая панель с контентом
        drawRect(width / 2 - 150, 0, width, height, 0xAA000000); // Полупрозрачный фон для контента
        for (GuiButton btn : contentButtons) {
            btn.drawButton(mc, mouseX, mouseY);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (categoryButtons.contains(button)) {
            selectedCategoryIndex = button.id;
            currentCategory = categoryNames[selectedCategoryIndex];
            scrollOffset = 0;
            updateContent();
            return;
        }

        if (contentButtons.contains(button)) {
            int idx = contentButtons.indexOf(button);
            String moduleName = "ExampleModule"; // Замени на реальный
            if (button.id >= 100 && button.id < 200) {
                ModuleManager.getInstance().getModule(moduleName).toggle();
                button.displayString = moduleName + ": " + (getModuleState(moduleName) ? "ON" : "OFF");
            } else if (button instanceof GuiSlider) {
                // Логика в mousePressed
            } else if (button instanceof GuiCheckbox) {
                ModuleManager.setValue("autoAttack", ((GuiCheckbox)button).isChecked());
            } else if (button instanceof GuiSelection) {
                ModuleManager.setValue("mode", ((GuiSelection)button).getSelected());
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && selectedCategoryIndex != -1) {
            scrollVelocity -= wheel / 120.0f * 10;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiButton btn : categoryButtons) {
            if (btn.mousePressed(mc, mouseX, mouseY)) {
                actionPerformed(btn);
            }
        }
        for (GuiButton btn : contentButtons) {
            if (btn.mousePressed(mc, mouseX, mouseY)) {
                actionPerformed(btn);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton == 0) {
            scrollOffset += Mouse.getDY();
            updateContent();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        scrollOffset += scrollVelocity;
        scrollVelocity *= 0.9f;
        updateContent();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RIGHTSHIFT) {
            mc.displayGuiScreen(null);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean getModuleState(String moduleName) {
        return ModuleManager.getInstance().getModule(moduleName).isEnabled();
    }
}