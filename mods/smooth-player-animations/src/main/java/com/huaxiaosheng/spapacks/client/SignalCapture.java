package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.anim.signal.ClipSignal;
import com.huaxiaosheng.spapacks.anim.signal.ItemRules;
import com.huaxiaosheng.spapacks.anim.signal.Signal;
import com.huaxiaosheng.spapacks.anim.signal.Signals;
import com.huaxiaosheng.spapacks.anim.signal.TickInput;
import com.huaxiaosheng.spapacks.anim.signal.WorldProbe;
import com.huaxiaosheng.spapacks.platform.Game;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

final class SignalCapture {
    private static final Set<String> WEAPON_SWINGS = Set.of("tool_sword_swing", "tool_axe_swing", "tool_trident_swing", "tool_mace_swing");

    private SignalCapture() {
    }

    static void tick(LivingEntity p, TickInput in) {
        Player player = p instanceof Player pl ? pl : null;
        AbstractClientPlayer client$ = p instanceof AbstractClientPlayer cp ? cp : null;
        Minecraft client = Minecraft.getInstance();
        in.local = p == client.player;
        in.x = p.getX();
        in.y = p.getY();
        in.z = p.getZ();
        in.velocityY = p.getDeltaMovement().y;
        in.bodyYaw = p.yBodyRot;
        in.limbSwing = p.walkAnimation.position();
        in.onGround = p.onGround();
        in.onSlime = in.onGround && p.level().getBlockState(p.getOnPos()).is(Blocks.SLIME_BLOCK);
        if (p instanceof LocalPlayer local) {
            in.movementKeys = Game.movementKeys(local);
            in.jumpKey = Game.jumpKey(local);
        }

        in.sneaking = p.isCrouching();
        in.sprinting = p.isSprinting();
        in.crawling = p.isVisuallyCrawling();
        in.swimming = p.isSwimming() || p.isVisuallySwimming() && p.isInWater();
        in.inWater = p.isInWater();
        in.submerged = p.isUnderWater();
        in.climbing = p.onClimbable();
        in.gliding = p.isFallFlying();
        in.flying = player != null && player.getAbilities().flying;
        in.sleeping = p.isSleeping();
        in.slowFalling = p.hasEffect(MobEffects.SLOW_FALLING);
        in.fallDistance = (float)p.fallDistance;
        Entity vehicle = p.getVehicle();
        in.vehicle = vehicle == null
            ? 0
            : (vehicle instanceof AbstractBoat ? 1 : (vehicle instanceof AbstractMinecart ? 2 : (vehicle instanceof AbstractHorse ? 3 : 4)));
        in.boatDriver = false;
        in.leftOar = false;
        in.rightOar = false;
        if (vehicle instanceof AbstractBoat boat) {
            in.boatDriver = boat.getControllingPassenger() == p;
            in.leftOar = boat.getPaddleState(0);
            in.rightOar = boat.getPaddleState(1);
            in.paddlePhase = boat.getRowingTime(0, 0.0F) / (Math.PI * 2);
        }

        in.hurtTime = p.hurtTime;
        in.swinging = Game.swinging(p);
        in.swingTicks = Game.swingTicks(p);
        InteractionHand swung = Game.swingHand(p);
        in.swingOffhand = swung == InteractionHand.OFF_HAND;
        in.swingSword = sword(p.getItemInHand(swung == null ? InteractionHand.MAIN_HAND : swung));
        in.holdSword = sword(p.getMainHandItem());
        in.using = p.isUsingItem();
        in.useAction = in.using ? useAction(p.getUseItem().getUseAnimation()) : 0;
        in.useOffhand = p.getUsedItemHand() == InteractionHand.OFF_HAND;
        in.useTicks = in.using ? p.getTicksUsingItem() : 0;
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        in.crossbowChargedMain = main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
        in.crossbowChargedOff = off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off);
        in.mainItem = id(main);
        in.offItem = id(off);
        in.mainSlot = player != null && player == Minecraft.getInstance().player ? Game.selectedSlot(player) : -1;
        in.lanternMain = lantern(main);
        in.lanternOff = lantern(off);
        in.mainArmLeft = p.getMainArm() == HumanoidArm.LEFT;
        in.bcAttacking = client$ != null && ExternalCapture.betterCombatAttacking(client$);
        in.bcArms = in.bcAttacking;
        in.bcPlaying = client$ != null && ExternalCapture.betterCombatPlaying(client$);
        in.externalBody = client$ != null && ExternalCapture.parCoolAnimating(client$);
        in.carrying = client$ != null && ExternalCapture.carrying(client$);
    }

    static void clips(LivingEntity p, Signals out) {
        ItemRules rules = SpaResources.items();
        InteractionHand swingHand = Game.swingHand(p) == null ? InteractionHand.MAIN_HAND : Game.swingHand(p);
        ItemStack swinging = p.getItemInHand(swingHand);
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        String mainId = id(main);
        String offId = id(off);
        String swingId = swinging == main ? mainId : (swinging == off ? offId : id(swinging));
        ItemRules.Rule swingRule = rule(rules, swingId, ItemRules.Type.SWING);
        out.clip(ClipSignal.SWING, swingRule != null ? swingRule.clip() : swing(swinging));
        String use = null;
        if (p.isUsingItem()) {
            ItemRules.Rule useRule = rule(rules, id(p.getUseItem()), ItemRules.Type.USE);
            use = useRule != null ? useRule.clip() : use(p.getUseItem().getUseAnimation());
        }

        out.clip(ClipSignal.USE, use);
        int kind = (int)out.get(Signal.RELEASE_KIND);
        out.clip(ClipSignal.RELEASE, kind == 5 ? "release_trident" : (kind == 6 ? "release_crossbow" : (kind == 4 ? "release_bow" : null)));
        ItemRules.Rule mainHold = rule(rules, mainId, ItemRules.Type.HOLD);
        ItemRules.Rule offHold = rule(rules, offId, ItemRules.Type.HOLD);
        String holdMain = mainHold != null ? mainHold.clip() : hold(main);
        String holdOff = offHold != null ? offHold.clip() : hold(off);
        out.clip(ClipSignal.HOLD_MAIN, holdMain);
        out.clip(ClipSignal.HOLD_OFF, holdOff);
        out.set(Signal.HOLDING, holdMain != null || holdOff != null);
        boolean both = mainHold != null
            && offHold != null
            && mainHold.enabled()
            && offHold.enabled()
            && mainHold.both() != null
            && mainHold.both().equals(offHold.both());
        out.clip(ClipSignal.HOLD_BOTH, both ? mainHold.both() : null);
        out.set(Signal.HOLD_BOTH, both);
        ItemRules.Rule idle = rule(rules, mainId, ItemRules.Type.IDLE);
        boolean idleItem = idle != null && idle.enabled();
        out.clip(ClipSignal.IDLE_LOOP, idleItem ? idle.animation() : null);
        out.clip(ClipSignal.IDLE_INTRO, idleItem ? idle.intro() : null);
        ItemRules.Rule drawRule = rule(rules, mainId, ItemRules.Type.DRAW);
        out.clip(ClipSignal.DRAW, drawRule != null ? (drawRule.enabled() ? drawRule.animation() : null) : draw(main, mainId));
        out.set(Signal.AFK_ITEM, idleItem);
        out.set(Signal.CEM_FULL, CemContent.full());
    }

    private static ItemRules.Rule rule(ItemRules packs, String id, ItemRules.Type type) {
        ItemRules.Rule own = ModConfig.itemRules().match(id, type);
        return own != null ? own : packs.match(id, type);
    }

    private static boolean sword(ItemStack stack) {
        ItemRules.Rule r = rule(SpaResources.items(), id(stack), ItemRules.Type.SWING);
        return r != null ? r.enabled() && "tool_sword_swing".equals(r.animation()) : stack.is(ItemTags.SWORDS);
    }

    private static String draw(ItemStack stack, String id) {
        if (stack.isEmpty()) {
            return null;
        }

        ItemRules.Rule r = rule(SpaResources.items(), id, ItemRules.Type.SWING);
        boolean weapon = r != null
            ? r.enabled() && WEAPON_SWINGS.contains(r.animation())
            : stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.TRIDENT) || mace(stack) || stack.getItem() instanceof ProjectileWeaponItem;
        return weapon ? "draw_weapon" : "draw_item";
    }

    private static boolean mace(ItemStack stack) {
        return stack.is(Items.MACE);
    }

    private static boolean lantern(ItemStack stack) {
        return stack.is(ItemTags.LANTERNS);
    }

    private static String swing(ItemStack stack) {
        if (stack.isEmpty()) {
            return "punch";
        } else if (stack.is(ItemTags.SWORDS)) {
            return "tool_sword_swing";
        } else if (stack.is(ItemTags.PICKAXES)) {
            return "tool_pickaxe_swing";
        } else if (stack.is(ItemTags.AXES)) {
            return "tool_axe_swing";
        } else if (stack.is(ItemTags.SHOVELS)) {
            return "tool_shovel_swing";
        } else if (stack.is(ItemTags.HOES)) {
            return "tool_hoe_swing";
        } else if (stack.is(Items.TRIDENT)) {
            return "tool_trident_swing";
        } else {
            return mace(stack) ? "tool_mace_swing" : "interact";
        }
    }

    private static String use(ItemUseAnimation action) {
        return switch (action) {
            case EAT -> "eating";
            case DRINK -> "drinking";
            case BLOCK -> "use_shield";
            case BOW -> "use_bow";
            case SPEAR -> "use_trident_throw";
            case CROSSBOW -> "use_crossbow";
            case SPYGLASS -> "use_spyglass";
            case TOOT_HORN -> "toot_horn";
            case BRUSH -> "brush";
            default -> null;
        };
    }

    private static String hold(ItemStack stack) {
        return stack.is(Items.FILLED_MAP) ? "hold_map" : null;
    }

    private static int useAction(ItemUseAnimation action) {
        return switch (action) {
            case EAT -> 1;
            case DRINK -> 2;
            case BLOCK -> 3;
            case BOW -> 4;
            case SPEAR -> 5;
            case CROSSBOW -> 6;
            case SPYGLASS -> 7;
            case TOOT_HORN -> 8;
            case BRUSH -> 9;
            default -> 0;
        };
    }

    private static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    static final class Probe implements WorldProbe {
        private LivingEntity player;

        SignalCapture.Probe at(LivingEntity player) {
            this.player = player;
            return this;
        }

        @Override
        public boolean slimeBelow() {
            Level world = this.player.level();

            for (int d = 0; d <= 4; d++) {
                BlockPos pos = BlockPos.containing(this.player.getX(), this.player.getY() - 0.2 - d, this.player.getZ());
                if (world.getBlockState(pos).is(Blocks.SLIME_BLOCK)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public int clearBelow(int max) {
            Level world = this.player.level();
            int clear = 0;

            for (int d = 1; d <= max; clear = d++) {
                BlockPos pos = BlockPos.containing(this.player.getX(), this.player.getY() - d, this.player.getZ());
                if (world.getBlockState(pos).isRedstoneConductor(world, pos)) {
                    break;
                }
            }

            return clear;
        }
    }
}
