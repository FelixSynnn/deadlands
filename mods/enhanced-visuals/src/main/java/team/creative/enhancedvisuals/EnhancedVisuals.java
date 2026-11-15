package team.creative.enhancedvisuals;

import java.util.Map.Entry;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.creative.creativecore.CreativeCore;
import team.creative.creativecore.ICreativeLoader;
import team.creative.creativecore.client.ClientLoader;
import team.creative.creativecore.common.CommonLoader;
import team.creative.creativecore.common.config.holder.ConfigHolderDynamic;
import team.creative.creativecore.common.config.holder.CreativeConfigRegistry;
import team.creative.creativecore.common.config.sync.ConfigSynchronization;
import team.creative.creativecore.common.network.CreativeNetwork;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.client.EVClient;
// import team.creative.enhancedvisuals.common.addon.coldsweat.ColdSweatAddon; (NeoForge-only, see onInitialize)
// import team.creative.enhancedvisuals.common.addon.survive.SurviveAddon; (NeoForge-only, see onInitialize)
// import team.creative.enhancedvisuals.common.addon.toughasnails.TANAddon; (NeoForge-only, see onInitialize)
import team.creative.enhancedvisuals.common.death.DeathMessages;
import team.creative.enhancedvisuals.common.event.EVEvents;
import team.creative.enhancedvisuals.common.handler.VisualHandlers;
import team.creative.enhancedvisuals.common.packet.DamagePacket;
import team.creative.enhancedvisuals.common.packet.ExplosionPacket;
import team.creative.enhancedvisuals.common.packet.PotionPacket;
import team.creative.enhancedvisuals.common.visual.VisualRegistry;
import team.creative.enhancedvisuals.server.EVManagerServer;

public class EnhancedVisuals implements CommonLoader, ClientLoader {
    public static final String MODID = "enhancedvisuals";
    public static final Logger LOGGER = LogManager.getLogger("enhancedvisuals");
    public static final CreativeNetwork NETWORK = new CreativeNetwork(1, LOGGER, Identifier.tryBuild("enhancedvisuals", "main"));
    public static EVEvents EVENTS;
    public static DeathMessages MESSAGES;
    public static EnhancedVisualsConfig CONFIG;
    public static EVManager MANAGER = new EVManagerServer();

    public EnhancedVisuals() {
        ICreativeLoader loader = CreativeCore.loader();
        loader.register(this);
        loader.registerClient(this);
    }

    @Override
    public void onInitializeClient() {
        CreativeCore.loader().registerClientStarted(EVClient::init);
    }

    @Override
    public void onInitialize() {
        NETWORK.registerType(ExplosionPacket.class, ExplosionPacket::new);
        NETWORK.registerType(DamagePacket.class, DamagePacket::new);
        NETWORK.registerType(PotionPacket.class, PotionPacket::new);
        ICreativeLoader loader = CreativeCore.loader();
        EVENTS = new EVEvents();
        VisualHandlers.init();
        MESSAGES = new DeathMessages();
        // DEADLANDS build: the Survive, Tough As Nails and Cold Sweat integrations are NeoForge-only
        // (common/addon/, kept on disk but excluded from this Fabric build in build.gradle).
        // if (loader.isModLoaded("survive")) {
        //     SurviveAddon.load();
        // }
        //
        // if (loader.isModLoaded("toughasnails")) {
        //     TANAddon.load();
        // }
        //
        // if (loader.isModLoaded("cold_sweat")) {
        //     ColdSweatAddon.load();
        // }

        ConfigHolderDynamic root = CreativeConfigRegistry.ROOT.registerFolder("enhancedvisuals");
        root.registerValue("general", CONFIG = new EnhancedVisualsConfig(), ConfigSynchronization.CLIENT, false);
        root.registerValue("messages", MESSAGES);
        ConfigHolderDynamic handlers = root.registerFolder("handlers", ConfigSynchronization.CLIENT);

        for (Entry<Identifier, VisualHandler> entry : VisualRegistry.entrySet()) {
            handlers.registerValue(entry.getKey().getPath(), entry.getValue());
        }
    }
}
