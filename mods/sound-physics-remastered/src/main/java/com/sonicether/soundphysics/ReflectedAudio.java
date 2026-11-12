package com.sonicether.soundphysics;

import com.sonicether.soundphysics.integration.voicechat.AudioChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class ReflectedAudio {
    private final List<Entry<Vec3, Double>> airspaceDirections;
    @Nullable
    private Entry<Vec3, Double> directDirection;
    private final double occlusion;
    private final Identifier sound;
    private int sharedAirspaces;

    public ReflectedAudio(double occlusion, Identifier sound) {
        this.occlusion = occlusion;
        this.sound = sound;
        this.airspaceDirections = new LinkedList<>();
    }

    public boolean shouldEvaluateDirection() {
        return SoundPhysicsMod.CONFIG.soundDirectionEvaluation.get()
            && (this.occlusion > 0.0 || !SoundPhysicsMod.CONFIG.redirectNonOccludedSounds.get())
            && !AudioChannel.isVoicechatSound(this.sound);
    }

    public int getSharedAirspaces() {
        return this.sharedAirspaces;
    }

    public void addDirectAirspace(Vec3 sharedAirspaceVector) {
        this.directDirection = Map.entry(sharedAirspaceVector, sharedAirspaceVector.length());
    }

    public void addSharedAirspace(Vec3 sharedAirspaceVector, double totalRayDistance) {
        double length = totalRayDistance + sharedAirspaceVector.length();
        if (!(length <= 0.0)
            && !(length > SoundPhysicsMod.CONFIG.maxSoundProcessingDistance.get() / SoundPhysicsMod.CONFIG.attenuationFactor.get().floatValue())) {
            this.sharedAirspaces++;
            if (this.shouldEvaluateDirection()) {
                this.airspaceDirections.add(Map.entry(sharedAirspaceVector, length));
            }
        }
    }

    @Nullable
    public Vec3 evaluateSoundPosition(Vec3 soundPos, Vec3 listenerPos) {
        if (!this.shouldEvaluateDirection()) {
            return null;
        }

        if (this.airspaceDirections.isEmpty()) {
            return null;
        }

        Vec3 sum;
        if (this.directDirection != null) {
            sum = this.directDirection.getKey().normalize();
        } else {
            sum = new Vec3(0.0, 0.0, 0.0);
        }

        for (Entry<Vec3, Double> direction : this.airspaceDirections) {
            double val = direction.getValue();
            if (val <= 0.0) {
                return null;
            }

            double w = 1.0 / (val * val);
            sum = sum.add(direction.getKey().normalize().scale(w));
        }

        Vec3 normalized = sum.normalize();
        return normalized.length() < 0.5 ? null : normalized.scale(soundPos.distanceTo(listenerPos)).add(listenerPos);
    }
}
