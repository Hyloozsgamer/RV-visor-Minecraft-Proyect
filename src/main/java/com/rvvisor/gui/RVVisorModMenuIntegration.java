package com.rvvisor.gui;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.optics.LensSettings;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class RVVisorModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            RVVisorMod mod = RVVisorMod.getInstance();
            LensSettings settings = (mod != null) ? mod.getLensSettings() : new LensSettings();
            return new RVVisorConfigScreen(parent, settings);
        };
    }
}
