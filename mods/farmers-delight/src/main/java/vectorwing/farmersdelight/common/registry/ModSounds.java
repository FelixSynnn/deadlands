package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModSounds {
    public static final Supplier<SoundEvent> BLOCK_STOVE_CRACKLE = RegUtils.regSound(
        "block.stove.crackle", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.stove.crackle"))
    );
    public static final Supplier<SoundEvent> BLOCK_COOKING_POT_BOIL = RegUtils.regSound(
        "block.cooking_pot.boil", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cooking_pot.boil"))
    );
    public static final Supplier<SoundEvent> BLOCK_COOKING_POT_BOIL_SOUP = RegUtils.regSound(
        "block.cooking_pot.boil_soup",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cooking_pot.boil_soup"))
    );
    public static final Supplier<SoundEvent> BLOCK_CUTTING_BOARD_PLACE = RegUtils.regSound(
        "block.cutting_board.place_item",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cutting_board.place_item"))
    );
    public static final Supplier<SoundEvent> BLOCK_CUTTING_BOARD_REMOVE = RegUtils.regSound(
        "block.cutting_board.remove_item",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cutting_board.remove_item"))
    );
    public static final Supplier<SoundEvent> BLOCK_CUTTING_BOARD_CARVE = RegUtils.regSound(
        "block.cutting_board.carve_tool",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cutting_board.carve_tool"))
    );
    public static final Supplier<SoundEvent> BLOCK_CUTTING_BOARD_KNIFE = RegUtils.regSound(
        "block.cutting_board.knife_cut",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cutting_board.knife_cut"))
    );
    public static final Supplier<SoundEvent> BLOCK_ROPE_FENCE_GATE_OPEN = RegUtils.regSound(
        "block.rope_fence_gate.open",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.rope_fence_gate.open"))
    );
    public static final Supplier<SoundEvent> BLOCK_ROPE_FENCE_GATE_CLOSE = RegUtils.regSound(
        "block.rope_fence_gate.close",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.rope_fence_gate.close"))
    );
    public static final Supplier<SoundEvent> BLOCK_CABINET_OPEN = RegUtils.regSound(
        "block.cabinet.open", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cabinet.open"))
    );
    public static final Supplier<SoundEvent> BLOCK_CABINET_CLOSE = RegUtils.regSound(
        "block.cabinet.close", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.cabinet.close"))
    );
    public static final Supplier<SoundEvent> BLOCK_SKILLET_SIZZLE = RegUtils.regSound(
        "block.skillet.sizzle", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.skillet.sizzle"))
    );
    public static final Supplier<SoundEvent> BLOCK_SKILLET_ADD_FOOD = RegUtils.regSound(
        "block.skillet.add_food", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.skillet.add_food"))
    );
    public static final Supplier<SoundEvent> ITEM_SKILLET_ATTACK_STRONG = RegUtils.regSound(
        "item.skillet.attack.strong",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "item.skillet.attack.strong"))
    );
    public static final Supplier<SoundEvent> ITEM_SKILLET_ATTACK_WEAK = RegUtils.regSound(
        "item.skillet.attack.weak", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "item.skillet.attack.weak"))
    );
    public static final Supplier<SoundEvent> BLOCK_TOMATOES_PICK_TOMATOES = RegUtils.regSound(
        "block.tomatoes.pick_tomatoes",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.tomatoes.pick_tomatoes"))
    );
    public static final Supplier<SoundEvent> BLOCK_FOOD_TAKE_PORTION = RegUtils.regSound(
        "block.food.take_portion", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.food.take_portion"))
    );
    public static final Supplier<SoundEvent> BLOCK_FOOD_SLICE = RegUtils.regSound(
        "block.food.slice", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "block.food.slice"))
    );
    public static final Supplier<SoundEvent> ENTITY_ROTTEN_TOMATO_THROW = RegUtils.regSound(
        "entity.rotten_tomato.throw",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "entity.rotten_tomato.throw"))
    );
    public static final Supplier<SoundEvent> ENTITY_ROTTEN_TOMATO_HIT = RegUtils.regSound(
        "entity.rotten_tomato.hit", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("farmersdelight", "entity.rotten_tomato.hit"))
    );

    public static void touch() {
    }
}
