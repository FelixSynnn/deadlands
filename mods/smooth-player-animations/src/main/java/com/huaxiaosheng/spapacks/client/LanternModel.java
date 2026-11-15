package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Game;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class LanternModel implements ConditionalItemModelProperty {
    public static final Identifier ID = Game.id("spa_packs", "lantern_animation");
    public static final MapCodec<LanternModel> CODEC = MapCodec.unit(new LanternModel());

    private LanternModel() {
    }

    @Override
    public boolean get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
        return true;
    }

    @Override
    public MapCodec<LanternModel> type() {
        return CODEC;
    }

    static void install() {
    }
}
