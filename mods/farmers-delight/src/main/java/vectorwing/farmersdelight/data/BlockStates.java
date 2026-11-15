package vectorwing.farmersdelight.data;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;

@Deprecated
public abstract class BlockStates extends FabricModelProvider {
    private static final int DEFAULT_ANGLE_OFFSET = 180;

    public BlockStates(FabricDataOutput output) {
        super(output);
    }
}
