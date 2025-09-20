package com.syrillix.syrillix.modules;

import com.syrillix.syrillixclient.modules.Module;
import com.syrillix.syrillixclient.modules.features.combat.ZombieKiller;
import com.syrillix.syrillixclient.modules.features.ender.EnderNodeESP;
import com.syrillix.syrillixclient.modules.features.ender.ZealotFarm;
import com.syrillix.syrillixclient.modules.features.fishing.AutoFish;
import com.syrillix.syrillixclient.modules.features.fishing.KillFish;
import com.syrillix.syrillixclient.modules.features.garden.AimToCleanBlock;
import com.syrillix.syrillixclient.modules.features.garden.CleanPlotESP;
import com.syrillix.syrillixclient.modules.features.mining.AutoMithril;
import com.syrillix.syrillixclient.modules.features.mining.EspMining;
import com.syrillix.syrillixclient.modules.features.render.InvHud;
import com.syrillix.syrillixclient.modules.features.render.TargetHUD;

import java.util.HashMap;
import java.util.Map;

public class ModuleManager {
    private static ModuleManager instance = new ModuleManager();
    private Map<String, Module> modules = new HashMap<>();

    private ModuleManager() {
        // Инициализация модулей, если нужно
        modules.put("KllFish", new KillFish());
        modules.put("AutoFish", new AutoFish());
        modules.put("ZombieKIller", new ZombieKiller());
        modules.put("ZealotFarm", new ZealotFarm());
        modules.put("AimToCleanBlock", new AimToCleanBlock());
        modules.put("CleanPlotESP", new CleanPlotESP());
        modules.put("AutoMithril", new AutoMithril());
        modules.put("InvHud", new InvHud());
        modules.put("EnderNodeESP", new EnderNodeESP());
        modules.put("EspMining", new EspMining());
        // Добавь остальные модули
    }

    public static ModuleManager getInstance() {
        return instance;
    }

    public Module getModule(String name) {
        return modules.get(name);
    }

    public void toggle(String name) {
        Module module = modules.get(name);
        if (module != null) module.toggle();
    }

    public boolean isEnabled(String name) {
        Module module = modules.get(name);
        return module != null && module.isEnabled();
    }
}