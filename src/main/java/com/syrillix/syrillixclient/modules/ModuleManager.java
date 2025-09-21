package com.syrillix.syrillixclient.modules;

import com.syrillix.syrillixclient.modules.features.combat.*;
import com.syrillix.syrillixclient.modules.features.ender.*;
import com.syrillix.syrillixclient.modules.features.garden.AimToCleanBlock;
import com.syrillix.syrillixclient.modules.features.garden.CleanPlotESP;
import com.syrillix.syrillixclient.modules.features.mining.AutoMithril;
import com.syrillix.syrillixclient.modules.features.mining.EspMining;
import com.syrillix.syrillixclient.modules.features.render.InvHud;
import com.syrillix.syrillixclient.modules.features.test.*;
import com.syrillix.syrillixclient.modules.features.fishing.*;
import com.syrillix.syrillixclient.gui.Category.CategoryType;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> moduleByName = new HashMap<>();
    private final Map<CategoryType, List<Module>> modulesByCategory = new EnumMap<>(CategoryType.class);

    public ModuleManager() {
        // Initialize empty module list
    }

    public void initializeModules() {
        // Register modules
        registerModule(new Robots());

        //fish
        registerModule(new AutoFish());
        registerModule(new KillFish());

        //123
        registerModule(new ZombieKiller());

        //ender
        registerModule(new EnderNodeESP());
        registerModule(new ZealotFarm());

        //garden
        registerModule(new CleanPlotESP());
        registerModule(new AimToCleanBlock());

// render
        registerModule(new InvHud());

        //mining
        registerModule(new AutoMithril());
        registerModule(new EspMining());
    }

    public void registerModule(Module module) {
        modules.add(module);
        moduleByName.put(module.getName().toLowerCase(), module);
        modulesByCategory.computeIfAbsent(module.getCategory().getType(), k -> new ArrayList<>()).add(module);
        MinecraftForge.EVENT_BUS.register(module);

    }

    public List<Module> getModules() {
        return modules;
    }

    public Module getModuleByName(String name) {
        return moduleByName.get(name.toLowerCase());
    }

    public Module getModuleClass(Class<Module> moduleClass) {
        for (Module module : modules) {
            if (module.getClass().equals(moduleClass)) {
                return module;
            }
        }
        return null;
    }

    public List<Module> getModulesByCategory(CategoryType category) {
        return modulesByCategory.getOrDefault(category, Collections.emptyList());
    }

    public List<Module> getEnabledModules() {
        return modules.stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }
}