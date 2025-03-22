package atoll.gui;

import atoll.gui.setting.Setting;
import atoll.modules.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickGui extends GuiScreen {
    private List<Category> categories = new ArrayList<>();
    private Category selectedCategory = null;
    private Map<Module, Boolean> moduleSettingsExpanded = new HashMap<>();
    private int scrollOffset = 0;

    // Dimensions and positions
    private int guiWidth = 500;
    private int guiHeight = 350;
    private int categoryWidth = 120;
    private int dragX, dragY;
    private boolean dragging = false;
    private int guiX, guiY;

    // Animation variables
    private float animationProgress = 0f;
    private long lastFrame = System.currentTimeMillis();

    // Colors - Modern dark theme with accent
    private static final Color BACKGROUND_COLOR = new Color(25, 25, 25, 255);
    private static final Color PANEL_COLOR = new Color(35, 35, 35, 255);
    private static final Color ACCENT_COLOR = new Color(0, 150, 255, 255);
    private static final Color ACCENT_COLOR_HOVER = new Color(0, 170, 255, 255);
    private static final Color TEXT_COLOR = new Color(220, 220, 220, 255);
    private static final Color SUBTEXT_COLOR = new Color(180, 180, 180, 255);
    private static final Color MODULE_COLOR = new Color(45, 45, 45, 255);
    private static final Color MODULE_HOVER_COLOR = new Color(55, 55, 55, 255);
    private static final Color MODULE_ENABLED_COLOR = new Color(0, 120, 215, 255);
    private static final Color SETTING_COLOR = new Color(40, 40, 40, 255);
    private static final Color SETTING_HOVER_COLOR = new Color(50, 50, 50, 255);
    private static final Color SEPARATOR_COLOR = new Color(60, 60, 60, 255);

    public ClickGui() {
        // Initialize moduleSettingsExpanded map
        for (Category category : Category.getCategories()) {
            for (Module module : category.getModules()) {
                moduleSettingsExpanded.put(module, false);
            }
        }
    }

    public void addCategory(Category category) {
        categories.add(category);
        if (selectedCategory == null) {
            selectedCategory = category;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        ScaledResolution sr = new ScaledResolution(mc);
        guiX = (sr.getScaledWidth() - guiWidth) / 2;
        guiY = (sr.getScaledHeight() - guiHeight) / 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Update animation
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastFrame) / 1000f;
        lastFrame = currentTime;

        if (animationProgress < 1f) {
            animationProgress += deltaTime * 5f; // 5 = animation speed
            if (animationProgress > 1f) animationProgress = 1f;
        }

        // Handle dragging
        if (dragging) {
            guiX = mouseX - dragX;
            guiY = mouseY - dragY;
        }

        // Draw main background with reduced alpha
        drawRect(0, 0, width, height, new Color(0, 0, 0, 120).getRGB());

        // Calculate animated sizes
        int animatedWidth = (int) (guiWidth * animationProgress);
        int animatedHeight = (int) (guiHeight * animationProgress);
        int animatedX = guiX + (guiWidth - animatedWidth) / 2;
        int animatedY = guiY + (guiHeight - animatedHeight) / 2;

        // Draw main panel with optimized rounded corners
        drawRoundedRect(animatedX, animatedY, animatedWidth, animatedHeight, 5, BACKGROUND_COLOR.getRGB());

        // Only draw contents if animation is mostly complete
        if (animationProgress > 0.9f) {
            // Draw title and drag bar
            drawGradientRect(guiX, guiY, guiX + guiWidth, guiY + 20,
                    new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 200).getRGB(),
                    new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 150).getRGB());

            drawCenteredString(fontRendererObj, "ATOLL Client", guiX + guiWidth / 2, guiY + 6, TEXT_COLOR.getRGB());

            // Draw separator
            drawRect(guiX, guiY + 20, guiX + guiWidth, guiY + 21, SEPARATOR_COLOR.getRGB());

            // Draw categories panel
            drawRoundedRect(guiX + 5, guiY + 25, categoryWidth - 10, guiHeight - 30, 3, PANEL_COLOR.getRGB());

            // Draw categories
            int categoryY = guiY + 30;
            for (Category category : categories) {
                boolean isHovered = isMouseOver(mouseX, mouseY, guiX + 5, categoryY, categoryWidth - 10, 25);
                boolean isSelected = category == selectedCategory;

                // Draw category background
                Color categoryColor = isSelected ? ACCENT_COLOR : (isHovered ? MODULE_HOVER_COLOR : MODULE_COLOR);
                drawRoundedRect(guiX + 5, categoryY, categoryWidth - 10, 25, 3, categoryColor.getRGB());

                // Draw category name
                drawCenteredString(fontRendererObj, category.getName(), guiX + 5 + (categoryWidth - 10) / 2,
                        categoryY + (25 - fontRendererObj.FONT_HEIGHT) / 2, TEXT_COLOR.getRGB());

                categoryY += 30;
            }

            // Draw modules panel
            int modulesX = guiX + categoryWidth;
            int modulesWidth = guiWidth - categoryWidth;

            drawRoundedRect(modulesX + 5, guiY + 25, modulesWidth - 10, guiHeight - 30, 3, PANEL_COLOR.getRGB());

            // Draw category title
            if (selectedCategory != null) {
                drawCenteredString(fontRendererObj, selectedCategory.getName() + " Modules",
                        modulesX + 5 + (modulesWidth - 10) / 2, guiY + 30, TEXT_COLOR.getRGB());

                // Draw separator
                drawRect(modulesX + 15, guiY + 42, modulesX + modulesWidth - 15, guiY + 43, SEPARATOR_COLOR.getRGB());

                // Draw modules
                int moduleY = guiY + 50;

                // Apply scrolling
                moduleY += scrollOffset;

                // Enable scissor test to clip modules that go outside the panel
                int scissorY = guiY + 50;
                int scissorHeight = guiHeight - 55;

                // Начало scissor test с правильными параметрами
                ScaledResolution sr = new ScaledResolution(mc);
                int scaleFactor = sr.getScaleFactor();

                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor((modulesX + 5) * scaleFactor,
                        mc.displayHeight - (scissorY + scissorHeight) * scaleFactor,
                        (modulesWidth - 10) * scaleFactor,
                        scissorHeight * scaleFactor);

                // Отрисовываем только видимые модули
                List<Module> visibleModules = new ArrayList<>();
                for (Module module : selectedCategory.getModules()) {
                    visibleModules.add(module);
                }

                for (Module module : visibleModules) {
                    // Проверяем, находится ли модуль в видимой области
                    if (moduleY + 25 >= scissorY && moduleY <= scissorY + scissorHeight) {
                        boolean isHovered = isMouseOver(mouseX, mouseY, modulesX + 10, moduleY, modulesWidth - 20, 25);
                        boolean isEnabled = module.isEnabled();
                        boolean isExpanded = moduleSettingsExpanded.getOrDefault(module, false);

                        // Draw module background
                        Color moduleColor = isEnabled ? MODULE_ENABLED_COLOR : (isHovered ? MODULE_HOVER_COLOR : MODULE_COLOR);
                        drawRoundedRect(modulesX + 10, moduleY, modulesWidth - 20, 25, 3, moduleColor.getRGB());

                        // Draw module name
                        drawString(fontRendererObj, module.getName(), modulesX + 15, moduleY + (25 - fontRendererObj.FONT_HEIGHT) / 2, TEXT_COLOR.getRGB());

                        // Draw expand/collapse indicator
                        String expandChar = isExpanded ? "-" : "+";
                        drawString(fontRendererObj, expandChar, modulesX + modulesWidth - 25, moduleY + (25 - fontRendererObj.FONT_HEIGHT) / 2, TEXT_COLOR.getRGB());

                        moduleY += 30;

                        // Draw settings if expanded
                        if (isExpanded) {
                            for (Setting setting : module.getSettings()) {
                                // Проверяем, находится ли настройка в видимой области
                                if (moduleY + 20 >= scissorY && moduleY <= scissorY + scissorHeight) {
                                    boolean settingHovered = isMouseOver(mouseX, mouseY, modulesX + 20, moduleY, modulesWidth - 40, 20);

                                    // Draw setting background
                                    drawRoundedRect(modulesX + 20, moduleY, modulesWidth - 40, 20, 2, settingHovered ? SETTING_HOVER_COLOR.getRGB() : SETTING_COLOR.getRGB());

                                    // Draw setting name
                                    drawString(fontRendererObj, setting.getName(), modulesX + 25, moduleY + (20 - fontRendererObj.FONT_HEIGHT) / 2, SUBTEXT_COLOR.getRGB());

                                    // Draw setting value based on type
                                    if (setting instanceof Setting.BooleanSetting) {
                                        Setting.BooleanSetting boolSetting = (Setting.BooleanSetting) setting;
                                        drawString(fontRendererObj, boolSetting.getValue() ? "ON" : "OFF",
                                                modulesX + modulesWidth - 55, moduleY + (20 - fontRendererObj.FONT_HEIGHT) / 2,
                                                boolSetting.getValue() ? ACCENT_COLOR.getRGB() : SUBTEXT_COLOR.getRGB());
                                    } else if (setting instanceof Setting.ModeSetting) {
                                        Setting.ModeSetting modeSetting = (Setting.ModeSetting) setting;
                                        drawString(fontRendererObj, modeSetting.getValue(),
                                                modulesX + modulesWidth - 55, moduleY + (20 - fontRendererObj.FONT_HEIGHT) / 2,
                                                SUBTEXT_COLOR.getRGB());
                                    } else if (setting instanceof Setting.NumberSetting) {
                                        Setting.NumberSetting numSetting = (Setting.NumberSetting) setting;
                                        drawString(fontRendererObj, String.format("%.1f", numSetting.getValue()),
                                                modulesX + modulesWidth - 55, moduleY + (20 - fontRendererObj.FONT_HEIGHT) / 2,
                                                SUBTEXT_COLOR.getRGB());

                                        // Draw slider
                                        int sliderX = modulesX + 25;
                                        int sliderY = moduleY + 15;
                                        int sliderWidth = modulesWidth - 90;

                                        // Background
                                        drawRect(sliderX, sliderY, sliderX + sliderWidth, sliderY + 2, new Color(30, 30, 30).getRGB());

                                        // Value
                                        float percentage = (numSetting.getValue() - numSetting.getMin()) / (numSetting.getMax() - numSetting.getMin());
                                        drawRect(sliderX, sliderY, sliderX + (int)(sliderWidth * percentage), sliderY + 2, ACCENT_COLOR.getRGB());

                                        // Slider knob - оптимизировано
                                        drawCircle(sliderX + (int)(sliderWidth * percentage), sliderY + 1, 3, ACCENT_COLOR_HOVER.getRGB());
                                    } else if (setting instanceof Setting.ColorSetting) {
                                        Setting.ColorSetting colorSetting = (Setting.ColorSetting) setting;
                                        // Draw color preview
                                        drawRect(modulesX + modulesWidth - 55, moduleY + 5, modulesX + modulesWidth - 35, moduleY + 15,
                                                colorSetting.getColor());
                                    }
                                }

                                moduleY += 25;
                            }
                        }
                    } else {
                        // Skip rendering but update Y position
                        moduleY += 30;
                        if (moduleSettingsExpanded.getOrDefault(module, false)) {
                            moduleY += 25 * module.getSettings().size();
                        }
                    }
                }

                // Отключаем scissor test после использования
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Check if clicking on title bar (for dragging)
        if (isMouseOver(mouseX, mouseY, guiX, guiY, guiWidth, 20)) {
            dragging = true;
            dragX = mouseX - guiX;
            dragY = mouseY - guiY;
            return;
        }

        // Check category clicks
        int categoryY = guiY + 30;
        for (Category category : categories) {
            if (isMouseOver(mouseX, mouseY, guiX + 5, categoryY, categoryWidth - 10, 25)) {
                selectedCategory = category;
                scrollOffset = 0; // Reset scroll when changing category
                return;
            }
            categoryY += 30;
        }

        // Check module clicks
        if (selectedCategory != null) {
            int modulesX = guiX + categoryWidth;
            int modulesWidth = guiWidth - categoryWidth;
            int moduleY = guiY + 50 + scrollOffset;

            // Calculate visible area
            int visibleAreaTop = guiY + 50;
            int visibleAreaBottom = guiY + guiHeight - 5;

            for (Module module : selectedCategory.getModules()) {
                boolean isExpanded = moduleSettingsExpanded.getOrDefault(module, false);

                // Check if module is in visible area
                if (moduleY + 25 >= visibleAreaTop && moduleY <= visibleAreaBottom) {
                    // Check if clicked on module
                    if (isMouseOver(mouseX, mouseY, modulesX + 10, moduleY, modulesWidth - 20, 25)) {
                        if (mouseButton == 0) { // Left click to toggle module
                            module.toggle();
                        } else if (mouseButton == 1) { // Right click to expand settings
                            moduleSettingsExpanded.put(module, !isExpanded);
                        }
                        return;
                    }
                }

                moduleY += 30;

                // Check settings clicks if expanded
                if (isExpanded) {
                    for (Setting setting : module.getSettings()) {
                        // Check if setting is in visible area
                        if (moduleY + 20 >= visibleAreaTop && moduleY <= visibleAreaBottom) {
                            if (isMouseOver(mouseX, mouseY, modulesX + 20, moduleY, modulesWidth - 40, 20)) {
                                if (setting instanceof Setting.BooleanSetting) {
                                    Setting.BooleanSetting boolSetting = (Setting.BooleanSetting) setting;
                                    boolSetting.setValue(!boolSetting.getValue());
                                } else if (setting instanceof Setting.ModeSetting) {
                                    Setting.ModeSetting modeSetting = (Setting.ModeSetting) setting;
                                    modeSetting.cycle();
                                } else if (setting instanceof Setting.NumberSetting) {
                                    Setting.NumberSetting numSetting = (Setting.NumberSetting) setting;

                                    // Handle slider click
                                    int sliderX = modulesX + 25;
                                    int sliderY = moduleY + 15;
                                    int sliderWidth = modulesWidth - 90;

                                    if (isMouseOver(mouseX, mouseY, sliderX, sliderY - 5, sliderWidth, 10)) {
                                        float percentage = (float)(mouseX - sliderX) / sliderWidth;
                                        percentage = Math.max(0, Math.min(1, percentage));
                                        float value = numSetting.getMin() + (numSetting.getMax() - numSetting.getMin()) * percentage;
                                        numSetting.setValue(value);
                                    }
                                }
                                return;
                            }
                        }
                        moduleY += 25;
                    }
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) {
            guiX = mouseX - dragX;
            guiY = mouseY - dragY;
            return;
        }

        // Handle slider dragging
        if (selectedCategory != null && clickedMouseButton == 0) {
            int modulesX = guiX + categoryWidth;
            int modulesWidth = guiWidth - categoryWidth;
            int moduleY = guiY + 50 + scrollOffset;

            for (Module module : selectedCategory.getModules()) {
                boolean isExpanded = moduleSettingsExpanded.getOrDefault(module, false);
                moduleY += 30;

                if (isExpanded) {
                    for (Setting setting : module.getSettings()) {
                        if (setting instanceof Setting.NumberSetting) {
                            Setting.NumberSetting numSetting = (Setting.NumberSetting) setting;

                            int sliderX = modulesX + 25;
                            int sliderY = moduleY + 15;
                            int sliderWidth = modulesWidth - 90;

                            if (isMouseOver(mouseX, mouseY, sliderX, sliderY - 5, sliderWidth, 10)) {
                                float percentage = (float)(mouseX - sliderX) / sliderWidth;
                                percentage = Math.max(0, Math.min(1, percentage));
                                float value = numSetting.getMin() + (numSetting.getMax() - numSetting.getMin()) * percentage;
                                numSetting.setValue(value);
                            }
                        }
                        moduleY += 25;
                    }
                }
            }
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            scrollOffset += scroll > 0 ? 20 : -20;
            // Limit scrolling
            int maxScrollOffset = 0;
            int totalHeight = 0;

            if (selectedCategory != null) {
                for (Module module : selectedCategory.getModules()) {
                    totalHeight += 30;
                    if (moduleSettingsExpanded.getOrDefault(module, false)) {
                        totalHeight += 25 * module.getSettings().size();
                    }
                }

                maxScrollOffset = Math.min(0, guiHeight - 55 - totalHeight);
                scrollOffset = Math.max(maxScrollOffset, Math.min(0, scrollOffset));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC key
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public List<Category> getCategories() {
        return categories;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Оптимизированный метод для отрисовки прямоугольника с закругленными углами
     */
    private void drawRoundedRect(int x, int y, int width, int height, int radius, int color) {
        // Оптимизация: не рисуем если вне экрана
        if (x + width < 0 || y + height < 0 || x > mc.displayWidth || y > mc.displayHeight) {
            return;
        }

        // Не рисуем круги если радиус слишком маленький
        if (radius < 1) {
            drawRect(x, y, x + width, y + height, color);
            return;
        }

        // Рисуем закругленные углы эффективнее
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.color(red, green, blue, alpha);

        // Оптимизация: вместо рисования окружностей по точкам, используем только основные сегменты
        int segments = Math.min(radius * 2, 16); // ограничиваем количество сегментов

        // Центральный прямоугольник
        drawRect(x + radius, y, x + width - radius, y + height, color);
        drawRect(x, y + radius, x + radius, y + height - radius, color);
        drawRect(x + width - radius, y + radius, x + width, y + height - radius, color);

        // Верхний левый угол
        drawCorner(x + radius, y + radius, radius, 180, 270, segments, color);

        // Верхний правый угол
        drawCorner(x + width - radius, y + radius, radius, 270, 360, segments, color);

        // Нижний левый угол
        drawCorner(x + radius, y + height - radius, radius, 90, 180, segments, color);

        // Нижний правый угол
        drawCorner(x + width - radius, y + height - radius, radius, 0, 90, segments, color);

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * Рисуем угол окружности с ограниченным количеством сегментов
     */
    private void drawCorner(int centerX, int centerY, int radius, int startAngle, int endAngle, int segments, int color) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();

        renderer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);

        // Центр круга
        renderer.pos(centerX, centerY, 0).endVertex();

        // Сегменты окружности
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(startAngle + (endAngle - startAngle) * (double)i / segments);
            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + Math.sin(angle) * radius;
            renderer.pos(x, y, 0).endVertex();
        }

        tessellator.draw();
    }

    /**
     * Оптимизированный метод для отрисовки круга
     */
    private void drawCircle(int x, int y, int radius, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.color(red, green, blue, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);

        // Центр круга
        renderer.pos(x, y, 0).endVertex();

        // Оптимизация: уменьшаем количество сегментов для маленьких кругов
        int segments = Math.min(radius * 4, 32);

        // Сегменты окружности
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2 * (double)i / segments;
            double xPos = x + Math.cos(angle) * radius;
            double yPos = y + Math.sin(angle) * radius;
            renderer.pos(xPos, yPos, 0).endVertex();
        }

        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}