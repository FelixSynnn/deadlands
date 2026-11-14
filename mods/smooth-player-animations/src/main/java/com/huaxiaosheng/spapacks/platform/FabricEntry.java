package com.huaxiaosheng.spapacks.platform;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.client.LanternModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class FabricEntry implements ClientModInitializer {
    public void onInitializeClient() {
        SpaPacks.init();
        ClientTickEvents.END_CLIENT_TICK.register(Platform::tick);

        for (final Platform.Reload r : Platform.RELOADS) {
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return r.id();
                }

                @Override
                public void onResourceManagerReload(ResourceManager manager) {
                    r.listener().accept(manager);
                }
            });
        }

        HudRenderCallback.EVENT.register(Platform::hud);
        ConditionalItemModelProperties.ID_MAPPER.put(LanternModel.ID, LanternModel.CODEC);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> player) {
                for (Platform.PlayerLayer layer : Platform.PLAYER_LAYERS) {
                    RenderLayer<AvatarRenderState, PlayerModel> made = Platform.make(layer, player);
                    if (made != null) {
                        helper.register(made);
                    }
                }
            }
        });
    }
}
