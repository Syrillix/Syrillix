package atoll.features;

import atoll.Main;
import atoll.features.combat.ZombieKiller;
import atoll.features.test.*;
//import atoll.features.foraging.*;
import atoll.features.fishing.*;
//import atoll.features.zealot.*;
// atoll.features.combat.*;
//import atoll.features.garden.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private List<Main.Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Initialize empty module list
    }

    public void initializeModules() {
        //test
        registerModule(new Robots());
        registerModule(new AutoFish());
        registerModule(new ZombieKiller());


    }

    public void registerModule(Main.Module module) {
        modules.add(module);
        Main.getInstance().registerModule(module);
    }

    public List<Main.Module> getModules() {
        return modules;
    }

    public Main.Module getModuleByName(String name) {
        for (Main.Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public List<Main.Module> getModulesByCategory(atoll.gui.Category.CategoryType category) {
        List<Main.Module> categoryModules = new ArrayList<>();
        for (Main.Module module : modules) {
            if (module.getCategory().getType() == category) {
                categoryModules.add(module);
            }
        }
        return categoryModules;
    }

    public List<Main.Module> getEnabledModules() {
        List<Main.Module> enabledModules = new ArrayList<>();
        for (Main.Module module : modules) {
            if (module.isEnabled()) {
                enabledModules.add(module);
            }
        }
        return enabledModules;
    }
}