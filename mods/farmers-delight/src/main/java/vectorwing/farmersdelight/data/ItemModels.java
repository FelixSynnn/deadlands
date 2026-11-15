package vectorwing.farmersdelight.data;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;

@Deprecated
public abstract class ItemModels extends FabricModelProvider {
    public ItemModels(FabricDataOutput output) {
        super(output);
    }
}
