package team.creative.enhancedvisuals.client;

import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import team.creative.creativecore.CreativeCore;
import team.creative.creativecore.client.CreativeCoreClient;
import team.creative.creativecore.common.util.type.list.Pair;
import team.creative.creativecore.common.util.type.list.PairList;
import team.creative.enhancedvisuals.EnhancedVisuals;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeBlur;
import team.creative.enhancedvisuals.api.type.VisualTypeFocus;
import team.creative.enhancedvisuals.api.type.VisualTypeSaturation;
import team.creative.enhancedvisuals.api.type.VisualTypeTexture;
import team.creative.enhancedvisuals.client.render.EVRenderer;
import team.creative.enhancedvisuals.client.type.VisualTypeBlurClient;
import team.creative.enhancedvisuals.client.type.VisualTypeClient;
import team.creative.enhancedvisuals.client.type.VisualTypeFocusClient;
import team.creative.enhancedvisuals.client.type.VisualTypeSaturationClient;
import team.creative.enhancedvisuals.client.type.VisualTypeTextureClient;

public class EVClient {
    private static final PairList<Class<? extends VisualType>, Function<? extends VisualType, VisualTypeClient>> CLIENT_TYPES = new PairList<>();

    public static VisualTypeClient get(VisualType type) {
        if (type.clientSideType == null) {
            Class<? extends VisualType> clazz = (Class<? extends VisualType>)type.getClass();
            Function factory = CLIENT_TYPES.getValue(clazz);
            if (factory == null) {
                int i = CLIENT_TYPES.size();

                while (--i >= 0) {
                    Pair<Class<? extends VisualType>, Function<? extends VisualType, VisualTypeClient>> pair = CLIENT_TYPES.get(i);
                    if (pair.key.isInstance(type)) {
                        factory = pair.value;
                        break;
                    }
                }
            }

            if (factory == null) {
                throw new IllegalArgumentException("No client handler found for type " + type);
            }

            type.clientSideType = factory.apply(type);
        }

        return (VisualTypeClient)type.clientSideType;
    }

    public static <T extends VisualType> void register(Class<T> clazz, Function<T, VisualTypeClient> factory) {
        CLIENT_TYPES.add(clazz, factory);
    }

    public static void init() {
        register(VisualTypeTexture.class, VisualTypeTextureClient::new);
        register(VisualTypeBlur.class, VisualTypeBlurClient::new);
        register(VisualTypeFocus.class, VisualTypeFocusClient::new);
        register(VisualTypeSaturation.class, VisualTypeSaturationClient::new);
        CreativeCore.loader().registerReloadListener(Identifier.tryBuild("enhancedvisuals", "resources"), new SimplePreparableReloadListener<Void>() {
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            protected void apply(Void object, ResourceManager manager, ProfilerFiller profiler) {
                EnhancedVisuals.MANAGER.clearEverything();
                EVRenderer.reloadResources = true;
            }
        });
        ResourceManager manager = Minecraft.getInstance().getResourceManager();

        for (VisualType type : VisualType.types()) {
            get(type).loadResources(manager);
        }

        CreativeCore.loader().registerClientRenderGui(EVRenderer::render);
        CreativeCoreClient.registerClientConfig("enhancedvisuals");
        EVManagerClient evmanager = new EVManagerClient();
        EnhancedVisuals.MANAGER = evmanager;
        CreativeCore.loader().registerClientTick(evmanager::clientTick);
    }

    public static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? !mc.player.isSpectator() && (!mc.player.isCreative() || EnhancedVisuals.CONFIG.doEffectsInCreative) : true;
    }

    public static boolean shouldTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || !mc.isPaused();
    }
}
