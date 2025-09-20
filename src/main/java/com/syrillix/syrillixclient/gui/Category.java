package com.syrillix.syrillixclient.gui;

import com.syrillix.syrillixclient.modules.Module;

import java.util.*;

public class Category {
    private static final Map<CategoryType, Category> categories = new EnumMap<>(CategoryType.class);
    private static boolean initialized = false;

    private final String name;
    private int x;
    private int y;
    private final List<Module> modules = new ArrayList<>();
    private boolean expanded = true;
    private final CategoryType type;

    public enum CategoryType {
        TEST("TEST"),
        GARDEN("Garden"),
        END("EnderWorld"),
        RENDER("Render"),
        COMBAT("Combat"),
        FISHING("Fishing"),
        MINING("Mining");

        private final String displayName;

        CategoryType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Category(CategoryType type, int x, int y) {
        this.type = type;
        this.name = type.getDisplayName();
        this.x = x;
        this.y = y;
    }

    public static void initializeCategories() {
        if (!initialized) {
            int startX = 10;
            int startY = 10;
            int spacing = 120;

            for (CategoryType type : CategoryType.values()) {
                categories.put(type, new Category(type, startX, startY));
                startX += spacing;
            }

            initialized = true;
        }
    }

    public static Category getCategory(CategoryType type) {
        initializeCategories(); // Ленивая инициализация
        return categories.get(type);
    }

    public static List<Category> getCategories() {
        initializeCategories(); // Ленивая инициализация
        return Collections.unmodifiableList(new ArrayList<>(categories.values()));
    }

    public String getName() {
        return name;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public void addModule(Module module) {
        modules.add(module);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    public CategoryType getType() {
        return type;
    }
}