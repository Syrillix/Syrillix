package com.syrillix.syrillixclient.gui.Settings;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
public abstract class Setting {
    private String name;
    public Setting(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public static class BooleanSetting extends Setting {
        private boolean value;
        private boolean defaultValue;
        public BooleanSetting(String name, boolean defaultValue) {
            super(name);
            this.value = defaultValue;
            this.defaultValue = defaultValue;
        }
        public boolean getValue() {
            return value;
        }
        public void setValue(boolean value) {
            this.value = value;
        }
        public void toggle() {
            this.value = !this.value;
        }
        public void reset() {
            this.value = defaultValue;
        }
    }
    public static class ModeSetting extends Setting {
        private int index;
        private List<String> modes;
        private int defaultIndex;
        public ModeSetting(String name, String defaultMode, String... modes) {
            super(name);
            this.modes = Arrays.asList(modes);
            this.defaultIndex = this.modes.indexOf(defaultMode);
            this.index = defaultIndex;
        }
        public String getValue() {
            return modes.get(index);
        }
        public void setValue(String value) {
            for (int i = 0; i < modes.size(); i++) {
                if (modes.get(i).equalsIgnoreCase(value)) {
                    index = i;
                    return;
                }
            }
        }
        public void cycle() {
            index = (index + 1) % modes.size();
        }
        public List<String> getModes() {
            return modes;
        }
        public void reset() {
            this.index = defaultIndex;
        }
    }
    public static class NumberSetting extends Setting {
        private float value;
        private float minimum;
        private float maximum;
        private float increment;
        private float defaultValue;
        public NumberSetting(String name, float defaultValue, float minimum, float maximum, float increment) {
            super(name);
            this.value = defaultValue;
            this.minimum = minimum;
            this.maximum = maximum;
            this.increment = increment;
            this.defaultValue = defaultValue;
        }
        public float getValue() {
            return value;
        }
        public void setValue(float value) {
            // Округляем до инкремента и ограничиваем пределами
            float roundedValue = Math.round(value / increment) * increment;
            this.value = Math.max(minimum, Math.min(maximum, roundedValue));
        }
        public void increment(boolean positive) {
            setValue(getValue() + (positive ? 1 : -1) * increment);
        }
        public float getMin() {
            return minimum;
        }
        public float getMax() {
            return maximum;
        }
        public float getIncrement() {
            return increment;
        }
        public void reset() {
            this.value = defaultValue;
        }
    }
    public static class KeybindSetting extends Setting {
        private int keyCode;
        private int defaultKeyCode;
        public KeybindSetting(String name, int defaultKeyCode) {
            super(name);
            this.keyCode = defaultKeyCode;
            this.defaultKeyCode = defaultKeyCode;
        }
        public int getKeyCode() {
            return keyCode;
        }
        public void setKeyCode(int keyCode) {
            this.keyCode = keyCode;
        }
        public void reset() {
            this.keyCode = defaultKeyCode;
        }
    }
    public static class ColorSetting extends Setting {
        private int red;
        private int green;
        private int blue;
        private int alpha;
        private int defaultRed;
        private int defaultGreen;
        private int defaultBlue;
        private int defaultAlpha;
        public ColorSetting(String name, int red, int green, int blue, int alpha) {
            super(name);
            this.red = Math.min(255, Math.max(0, red));
            this.green = Math.min(255, Math.max(0, green));
            this.blue = Math.min(255, Math.max(0, blue));
            this.alpha = Math.min(255, Math.max(0, alpha));
            this.defaultRed = this.red;
            this.defaultGreen = this.green;
            this.defaultBlue = this.blue;
            this.defaultAlpha = this.alpha;
        }
        public int getRed() {
            return red;
        }
        public void setRed(int red) {
            this.red = Math.min(255, Math.max(0, red));
        }
        public int getGreen() {
            return green;
        }
        public void setGreen(int green) {
            this.green = Math.min(255, Math.max(0, green));
        }
        public int getBlue() {
            return blue;
        }
        public void setBlue(int blue) {
            this.blue = Math.min(255, Math.max(0, blue));
        }
        public int getAlpha() {
            return alpha;
        }
        public void setAlpha(int alpha) {
            this.alpha = Math.min(255, Math.max(0, alpha));
        }
        public int getColor() {
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        public Color getColorObj() {
            return new Color(red, green, blue, alpha);
        }
        public void setColor(int red, int green, int blue, int alpha) {
            this.red = Math.min(255, Math.max(0, red));
            this.green = Math.min(255, Math.max(0, green));
            this.blue = Math.min(255, Math.max(0, blue));
            this.alpha = Math.min(255, Math.max(0, alpha));
        }
        public void setColor(Color color) {
            this.red = color.getRed();
            this.green = color.getGreen();
            this.blue = color.getBlue();
            this.alpha = color.getAlpha();
        }
        public void reset() {
            this.red = defaultRed;
            this.green = defaultGreen;
            this.blue = defaultBlue;
            this.alpha = defaultAlpha;
        }
    }
    public static class StringSetting extends Setting {
        private String value;
        private String defaultValue;
        public StringSetting(String name, String defaultValue) {
            super(name);
            this.value = defaultValue;
            this.defaultValue = defaultValue;
        }
        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
        public void reset() {
            this.value = defaultValue;
        }
    }
    public static class GroupSetting extends Setting {
        private boolean expanded;
        private List<Setting> settings;
        public GroupSetting(String name, Setting... settings) {
            super(name);
            this.settings = Arrays.asList(settings);
            this.expanded = false;
        }
        public List<Setting> getSettings() {
            return settings;
        }
        public boolean isExpanded() {
            return expanded;
        }
        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }
        public void toggle() {
            this.expanded = !this.expanded;
        }
    }
    public static class ListSetting<T> extends Setting {
        private List<T> items;
        private int selectedIndex;
        private int defaultSelectedIndex;
        public ListSetting(String name, List<T> items, int defaultSelectedIndex) {
            super(name);
            this.items = items;
            this.defaultSelectedIndex = Math.max(0, Math.min(defaultSelectedIndex, items.size() - 1));
            this.selectedIndex = this.defaultSelectedIndex;
        }
        public T getSelected() {
            return items.get(selectedIndex);
        }
        public void setSelected(int index) {
            this.selectedIndex = Math.max(0, Math.min(index, items.size() - 1));
        }
        public void setSelected(T item) {
            int index = items.indexOf(item);
            if (index != -1) {
                this.selectedIndex = index;
            }
        }
        public void cycle() {
            selectedIndex = (selectedIndex + 1) % items.size();
        }
        public List<T> getItems() {
            return items;
        }
        public int getSelectedIndex() {
            return selectedIndex;
        }
        public void reset() {
            this.selectedIndex = defaultSelectedIndex;
        }
    }
    public static class SliderSetting extends NumberSetting {
        private boolean dragging;
        public SliderSetting(String name, float defaultValue, float minimum, float maximum, float increment) {
            super(name, defaultValue, minimum, maximum, increment);
            this.dragging = false;
        }
        public boolean isDragging() {
            return dragging;
        }
        public void setDragging(boolean dragging) {
            this.dragging = dragging;
        }
        public float getPercentage() {
            return (getValue() - getMin()) / (getMax() - getMin());
        }
        public void setValueFromPercentage(float percentage) {
            setValue(getMin() + (getMax() - getMin()) * percentage);
        }
    }
    public static class ButtonSetting extends Setting {
        private Runnable action;
        public ButtonSetting(String name, Runnable action) {
            super(name);
            this.action = action;
        }
        public void click() {
            if (action != null) {
                action.run();
            }
        }
    }
    public static class PositionSetting extends Setting {
        private float x;
        private float y;
        private float defaultX;
        private float defaultY;
        public PositionSetting(String name, float defaultX, float defaultY) {
            super(name);
            this.x = defaultX;
            this.y = defaultY;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
        }
        public float getX() {
            return x;
        }
        public void setX(float x) {
            this.x = x;
        }
        public float getY() {
            return y;
        }
        public void setY(float y) {
            this.y = y;
        }
        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
        public void reset() {
            this.x = defaultX;
            this.y = defaultY;
        }
    }
}