package com.sonicether.soundphysics.integration.voicechat;

import com.sonicether.soundphysics.SoundPhysics;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class AudioChannel {
    private static final String VOICECHAT = "voicechat";
    private static final Identifier VOICECHAT_SOUND = Identifier.fromNamespaceAndPath("voicechat", "voicechat");
    private final UUID channelId;
    private long lastUpdate;
    private Vec3 lastPos;

    public AudioChannel(UUID channelId) {
        this.channelId = channelId;
    }

    public void onSound(int source, @Nullable Vec3 soundPos, float directGainScale, @Nullable String category) {
        if (soundPos == null) {
            SoundPhysics.setDefaultEnvironment(source, directGainScale);
        } else {
            long time = System.currentTimeMillis();
            if (time - this.lastUpdate >= 500L || this.lastPos == null || !(this.lastPos.distanceTo(soundPos) < 1.0)) {
                Identifier sound = category == null ? VOICECHAT_SOUND : Identifier.fromNamespaceAndPath("voicechat", category);
                SoundPhysics.processSound(source, soundPos.x(), soundPos.y(), soundPos.z(), SoundSource.MASTER, sound, directGainScale);
                this.lastUpdate = time;
                this.lastPos = soundPos;
            }
        }
    }

    public UUID getChannelId() {
        return this.channelId;
    }

    public boolean canBeRemoved() {
        return System.currentTimeMillis() - this.lastUpdate > 5000L;
    }

    public static boolean isVoicechatSound(Identifier sound) {
        return sound.getNamespace().equals("voicechat");
    }
}
