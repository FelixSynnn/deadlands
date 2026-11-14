package team.creative.enhancedvisuals.client.sound;

import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;

public class TickedSound extends PositionedSound implements TickableSoundInstance {
    public int tick = 0;
    public DecimalCurve volumeGraph;

    public TickedSound(Identifier identifier, SoundSource category, float pitch, DecimalCurve volumeGraph) {
        super(identifier, category, (float)volumeGraph.valueAt(0.0), pitch);
        this.volumeGraph = volumeGraph;
        this.looping = true;
    }

    public TickedSound(Identifier identifier, SoundSource category, float pitch, BlockPos pos, DecimalCurve volumeGraph) {
        super(identifier, category, (float)volumeGraph.valueAt(0.0), pitch, pos);
        this.volumeGraph = volumeGraph;
        this.looping = true;
    }

    public boolean isStopped() {
        return this.volume == 0.0F;
    }

    public void stop() {
        this.volume = 0.0F;
        this.tick = (int)Math.ceil(this.volumeGraph.max);
    }

    public void tick() {
        this.tick++;
        this.volume = (float)this.volumeGraph.valueAt(this.tick);
    }
}
