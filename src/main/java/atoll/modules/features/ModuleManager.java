package atoll.modules.features;

import atoll.Main;
import atoll.modules.features.combat.ZombieKiller;
import atoll.modules.features.test.*;
import atoll.modules.features.fishing.*;
import atoll.gui.Category.CategoryType;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Main.Module> modules = new ArrayList<>();
    private final Map<String, Main.Module> moduleByName = new HashMap<>();
    private final Map<CategoryType, List<Main.Module>> modulesByCategory = new EnumMap<>(CategoryType.class);

    public ModuleManager() {
        // Initialize empty module list
    }

    public void initializeModules() {
        // Register modules
        registerModule(new Robots());
        registerModule(new AutoFish());
        registerModule(new ZombieKiller());
    }

    public void registerModule(Main.Module module) {
        modules.add(module);
        moduleByName.put(module.getName().toLowerCase(), module);
        modulesByCategory.computeIfAbsent(module.getCategory().getType(), k -> new ArrayList<>()).add(module);
        Main.getInstance().registerModule(module);
    }

    public List<Main.Module> getModules() {
        return modules;
    }

    public Main.Module getModuleByName(String name) {
        return moduleByName.get(name.toLowerCase());
    }

    public Main.Module getModuleClass(Class<Main.Module> moduleClass) {
        for (Main.Module module : modules) {
            if (module.getClass().equals(moduleClass)) {
                return module;
            }
        }
        return null;
    }

    public List<Main.Module> getModulesByCategory(CategoryType category) {
        return modulesByCategory.getOrDefault(category, Collections.emptyList());
    }

    public List<Main.Module> getEnabledModules() {
        return modules.stream()
                .filter(Main.Module::isEnabled)
                .collect(Collectors.toList());
    }
}