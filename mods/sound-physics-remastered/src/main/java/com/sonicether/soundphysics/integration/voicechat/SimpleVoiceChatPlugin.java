package com.sonicether.soundphysics.integration.voicechat;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.SoundPhysicsMod;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.CreateOpenALContextEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTThreadLocalContext;

@ForgeVoicechatPlugin
public class SimpleVoiceChatPlugin implements VoicechatPlugin {
    private static final UUID OWN_VOICE_ID = UUID.randomUUID();
    public static String OWN_VOICE_CATEGORY = "own_voice";
    private final Map<UUID, AudioChannel> audioChannels = new HashMap<>();
    private ClientLocationalAudioChannel locationalAudioChannel;

    public String getPluginId() {
        return "sound_physics_remastered";
    }

    public void initialize(VoicechatApi api) {
        Loggers.log("Initializing Simple Voice Chat integration");
        this.audioChannels.clear();
    }

    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(CreateOpenALContextEvent.class, this::onCreateALContext);
        registration.registerEvent(OpenALSoundEvent.class, this::onOpenALSound);
        registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VolumeCategory ownVoice = event.getVoicechat()
            .volumeCategoryBuilder()
            .setId(OWN_VOICE_CATEGORY)
            .setName("Own voice")
            .setNameTranslationKey("category.sound_physics_remastered.own_voice")
            .setDescription("The volume of your own voice")
            .setDescriptionTranslationKey("category.sound_physics_remastered.own_voice.description")
            .build();
        event.getVoicechat().registerVolumeCategory(ownVoice);
    }

    private void onClientSound(ClientSoundEvent event) {
        if (this.locationalAudioChannel != null) {
            if (SoundPhysicsMod.CONFIG.hearSelf.get()) {
                Vec3 position = Minecraft.getInstance().player.position();
                this.locationalAudioChannel.setCategory(OWN_VOICE_CATEGORY);
                this.locationalAudioChannel.setLocation(event.getVoicechat().createPosition(position.x, position.y, position.z));
                this.locationalAudioChannel.play(event.getRawAudio());
            }
        }
    }

    private void onCreateALContext(CreateOpenALContextEvent event) {
        long oldContext = EXTThreadLocalContext.alcGetThreadContext();
        EXTThreadLocalContext.alcSetThreadContext(event.getContext());
        Loggers.log("Initializing sound physics for voice chat audio");
        SoundPhysics.init();
        EXTThreadLocalContext.alcSetThreadContext(oldContext);
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        Loggers.logDebug("Clearing unused audio channels");
        this.audioChannels.values().removeIf(AudioChannel::canBeRemoved);
        this.locationalAudioChannel = event.getVoicechat().createLocationalAudioChannel(OWN_VOICE_ID, event.getVoicechat().createPosition(0.0, 0.0, 0.0));
    }

    private void onOpenALSound(OpenALSoundEvent event) {
        if (SoundPhysicsMod.CONFIG.simpleVoiceChatIntegration.get()) {
            Position position = event.getPosition();
            UUID channelId = event.getChannelId();
            if (channelId != null) {
                if (SoundPhysicsMod.CONFIG.enabled.get()) {
                    SoundPhysics.applyReferenceDistanceWorkaround(event.getSource(), AL10.alGetSourcef(event.getSource(), 4131));
                }

                boolean auxOnly = SoundPhysicsMod.CONFIG.hearSelf.get() && OWN_VOICE_ID.equals(channelId);
                AudioChannel audioChannel = this.audioChannels.get(channelId);
                if (audioChannel == null) {
                    audioChannel = new AudioChannel(channelId);
                    this.audioChannels.put(channelId, audioChannel);
                }

                audioChannel.onSound(
                    event.getSource(),
                    position == null ? null : new Vec3(position.getX(), position.getY(), position.getZ()),
                    getDirectGainScale(event.getVoicechat(), auxOnly),
                    event.getCategory()
                );
            }
        }
    }

    private static float getDirectGainScale(VoicechatClientApi api, boolean auxOnly) {
        if (auxOnly) {
            return 0.0F;
        } else {
            return SoundPhysics.hasReferenceDistanceBug() && api.getClientConfig().getString("audio_type", "NORMAL").equals("NORMAL") ? 0.5F : 1.0F;
        }
    }
}
