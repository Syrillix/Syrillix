package atoll.gui;

import atoll.Main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Category {
    private static Map<CategoryType, Category> categories = new HashMap<>();
    private static boolean initialized = false;

    private String name;
    private int x;
    private int y;
    private List<Main.Module> modules = new ArrayList<>();
    private boolean expanded = true;
    private CategoryType type;

    public enum CategoryType {
        TEST("TEST"),
        GARDEN("Garden"),
        ZEALOT("Zealot"),
        FORAGING("Foraging"),
        COMBAT("Combat"),
        FISHING("Fishing");


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
        if (!initialized) {
            initializeCategories();
        }
        return categories.get(type);
    }

    public static List<Category> getCategories() {
        if (!initialized) {
            initializeCategories();
        }
        return new ArrayList<>(categories.values());
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

    public List<Main.Module> getModules() {
        return modules;
    }

    public void addModule(Main.Module module) {
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