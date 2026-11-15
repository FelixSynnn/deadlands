package com.huaxiaosheng.spapacks.platform;

import com.huaxiaosheng.spapacks.client.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return Platform.isModLoaded(Platform.clothConfigId()) ? parent -> ConfigScreen.create(parent) : parent -> null;
    }
}
