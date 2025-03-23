package atoll.modules;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private int keyBind;
    private boolean enabled;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    public final Minecraft mc = Minecraft.getMinecraft();

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