package team.creative.enhancedvisuals.common.handler;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.Side;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.premade.DecimalMinMax;
import team.creative.creativecore.common.config.premade.IntMinMax;
import team.creative.creativecore.common.util.type.Color;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeParticleColored;

public class RainHandler extends VisualHandler {
    @CreativeConfig
    public IntMinMax duration = new IntMinMax(100, 100);
    @CreativeConfig
    public VisualType drop = new VisualTypeParticleColored("drop", 0, new DecimalMinMax(0.1, 0.5), new Color(41, 76, 149));
    @CreativeConfig
    public IntMinMax delay = new IntMinMax(0, 0);
    @CreativeConfig
    public IntMinMax amount = new IntMinMax(1, 1);
    private int timer = 0;
    private int nextDelay = -1;
    private Random rand = new Random();

    @Override
    public void tick(@Nullable Player player) {
        if (player != null) {
            BlockPos blockpos = player.blockPosition();
            if (!player.level().isRainingAt(blockpos)
                && !player.level().isRainingAt(BlockPos.containing(blockpos.getX(), player.getBoundingBox().maxY, blockpos.getZ()))) {
                this.timer = 0;
            } else {
                this.timer++;
                if (this.nextDelay == -1) {
                    this.nextDelay = this.delay.next(this.rand);
                }

                if (this.timer >= this.nextDelay) {
                    this.addParticlesFadeOut(this.drop, this, this.amount.next(this.rand), this.duration, true);
                    this.timer = 0;
                    this.nextDelay = this.delay.next(this.rand);
                }
            }
        }
    }

    @Override
    public void configured(Side side) {
        super.configured(side);
        this.nextDelay = -1;
    }
}
