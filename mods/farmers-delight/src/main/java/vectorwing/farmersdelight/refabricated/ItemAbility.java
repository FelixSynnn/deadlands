package vectorwing.farmersdelight.refabricated;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.Holder;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.common.tag.CommonTags;

public enum ItemAbility implements StringRepresentable {
    SWORD_DIG,
    SHOVEL_DIG,
    PICKAXE_DIG,
    SHEARS_CARVE,
    SHEARS_DIG,
    SHEARS_HARVEST,
    AXE_DIG,
    AXE_STRIP,
    HOE_DIG,
    KNIFE_DIG,
    KNIFE_HARVEST;

    public static final Codec<ItemAbility> CODEC = StringRepresentable.fromEnum(ItemAbility::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean canPerformAction(@NonNull ItemStack stack) {
        return this.canPerformAction(stack.getItem().builtInRegistryHolder());
    }

    public boolean canPerformAction(@NonNull Holder<Item> itemHolder) {
        return switch (this) {
            case SWORD_DIG -> itemHolder.is(ItemTags.SWORDS);
            case SHOVEL_DIG -> itemHolder.is(ItemTags.SHOVELS);
            case PICKAXE_DIG -> itemHolder.is(ItemTags.PICKAXES);
            case SHEARS_CARVE, SHEARS_DIG, SHEARS_HARVEST -> itemHolder.is(ConventionalItemTags.SHEAR_TOOLS);
            case AXE_DIG, AXE_STRIP -> itemHolder.is(ItemTags.AXES);
            case HOE_DIG -> itemHolder.is(ItemTags.HOES);
            case KNIFE_DIG, KNIFE_HARVEST -> itemHolder.is(CommonTags.Items.TOOLS_KNIFE);
        };
    }
}
