package vectorwing.farmersdelight.common.crafting.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.Configuration;

public record ChanceResult(ItemStack stack, float chance) {
    public static final ChanceResult EMPTY = new ChanceResult(null, 1.0F);
    public static final Codec<ChanceResult> CODEC = RecordCodecBuilder.create(
        inst -> inst.group(
                ItemStack.CODEC.fieldOf("item").forGetter(ChanceResult::stack), Codec.FLOAT.optionalFieldOf("chance", 1.0F).forGetter(ChanceResult::chance)
            )
            .apply(inst, ChanceResult::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChanceResult> STREAM_CODEC = StreamCodec.composite(
        ItemStack.STREAM_CODEC, ChanceResult::stack, ByteBufCodecs.FLOAT, ChanceResult::chance, ChanceResult::new
    );

    public ItemStack rollOutput(RandomSource random, int fortuneLevel) {
        int outputAmount = this.stack.getCount();
        double fortuneBonus = Configuration.CUTTING_BOARD_FORTUNE_BONUS.get() * fortuneLevel;

        for (int roll = 0; roll < this.stack.getCount(); roll++) {
            if (random.nextFloat() > this.chance + fortuneBonus) {
                outputAmount--;
            }
        }

        if (outputAmount == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = this.stack.copy();
        out.setCount(outputAmount);
        return out;
    }
}
