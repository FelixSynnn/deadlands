package vectorwing.farmersdelight.common.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.server.level.ServerPlayer;
import vectorwing.farmersdelight.common.registry.ModAdvancements;

public class CuttingBoardTrigger extends SimpleCriterionTrigger<CuttingBoardTrigger.TriggerInstance> {
    @Override
    public Codec<CuttingBoardTrigger.TriggerInstance> codec() {
        return CuttingBoardTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, CuttingBoardTrigger.TriggerInstance::test);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance {
        public static final Codec<CuttingBoardTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            builder -> builder.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(CuttingBoardTrigger.TriggerInstance::player))
                .apply(builder, CuttingBoardTrigger.TriggerInstance::new)
        );

        public static Criterion<CuttingBoardTrigger.TriggerInstance> simple() {
            return ModAdvancements.USE_CUTTING_BOARD.get().createCriterion(new CuttingBoardTrigger.TriggerInstance(Optional.empty()));
        }

        public boolean test() {
            return true;
        }
    }
}
