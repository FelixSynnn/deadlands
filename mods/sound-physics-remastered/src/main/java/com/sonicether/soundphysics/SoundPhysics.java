package com.sonicether.soundphysics;

import com.sonicether.soundphysics.config.ReverbParams;
import com.sonicether.soundphysics.debug.RaycastRenderer;
import com.sonicether.soundphysics.profiling.TaskProfiler;
import com.sonicether.soundphysics.utils.LevelAccessUtils;
import com.sonicether.soundphysics.utils.RaycastUtils;
import com.sonicether.soundphysics.utils.SoundRateManager;
import com.sonicether.soundphysics.world.ClientLevelProxy;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3f;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

public class SoundPhysics {
    private static final float PHI = 1.618034F;
    private static final Pattern AMBIENT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+:ambient\\..*$");
    private static final Pattern BLOCK_PATTERN = Pattern.compile(".*block..*");
    private static final Pattern REFERENCE_DISTANCE_BUG_VERSION = Pattern.compile("ALSOFT 1\\.(22|23)\\.");
    private static int auxFXSlot0;
    private static int auxFXSlot1;
    private static int auxFXSlot2;
    private static int auxFXSlot3;
    private static int reverb0;
    private static int reverb1;
    private static int reverb2;
    private static int reverb3;
    private static int directFilter0;
    private static int sendFilter0;
    private static int sendFilter1;
    private static int sendFilter2;
    private static int sendFilter3;
    private static Minecraft minecraft;
    private static TaskProfiler profiler;
    private static SoundSource lastSoundCategory;
    private static Identifier lastSound;
    @Nullable
    private static Boolean referenceDistanceBug;
    private static int maxAuxSends;

    public static void init() {
        Loggers.log("Initializing Sound Physics");
        setupEFX();
        Loggers.log("EFX ready");
        minecraft = Minecraft.getInstance();
        profiler = new TaskProfiler("Sound Physics");
    }

    public static void syncReverbParams() {
        if (auxFXSlot0 != 0) {
            setReverbParams(ReverbParams.getReverb0(), auxFXSlot0, reverb0);
            setReverbParams(ReverbParams.getReverb1(), auxFXSlot1, reverb1);
            setReverbParams(ReverbParams.getReverb2(), auxFXSlot2, reverb2);
            setReverbParams(ReverbParams.getReverb3(), auxFXSlot3, reverb3);
        }
    }

    static void setupEFX() {
        long currentContext = ALC10.alcGetCurrentContext();
        long currentDevice = ALC10.alcGetContextsDevice(currentContext);
        if (ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
            Loggers.log("EFX Extension recognized");
            maxAuxSends = ALC10.alcGetInteger(currentDevice, 131075);
            Loggers.log("Max auxiliary sends: {}", maxAuxSends);
            auxFXSlot0 = EXTEfx.alGenAuxiliaryEffectSlots();
            Loggers.log("Aux slot {} created", auxFXSlot0);
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot0, 3, 1);
            auxFXSlot1 = EXTEfx.alGenAuxiliaryEffectSlots();
            Loggers.log("Aux slot {} created", auxFXSlot1);
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot1, 3, 1);
            auxFXSlot2 = EXTEfx.alGenAuxiliaryEffectSlots();
            Loggers.log("Aux slot {} created", auxFXSlot2);
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot2, 3, 1);
            auxFXSlot3 = EXTEfx.alGenAuxiliaryEffectSlots();
            Loggers.log("Aux slot {} created", auxFXSlot3);
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot3, 3, 1);
            Loggers.logALError("Failed creating auxiliary effect slots");
            reverb0 = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverb0, 32769, 32768);
            Loggers.logALError("Failed creating reverb effect slot 0");
            reverb1 = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverb1, 32769, 32768);
            Loggers.logALError("Failed creating reverb effect slot 1");
            reverb2 = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverb2, 32769, 32768);
            Loggers.logALError("Failed creating reverb effect slot 2");
            reverb3 = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverb3, 32769, 32768);
            Loggers.logALError("Failed creating reverb effect slot 3");
            directFilter0 = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(directFilter0, 32769, 1);
            Loggers.logDebug("directFilter0: {}", directFilter0);
            sendFilter0 = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter0, 32769, 1);
            Loggers.logDebug("filter0: {}", sendFilter0);
            sendFilter1 = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter1, 32769, 1);
            Loggers.logDebug("filter1: {}", sendFilter1);
            sendFilter2 = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter2, 32769, 1);
            Loggers.logDebug("filter2: {}", sendFilter2);
            sendFilter3 = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter3, 32769, 1);
            Loggers.logDebug("filter3: {}", sendFilter3);
            Loggers.logALError("Error creating lowpass filters");
            syncReverbParams();
        } else {
            Loggers.error("EFX Extension not found on current device. Aborting.");
        }
    }

    public static void applyReferenceDistanceWorkaround(int sourceID, float maxDistance) {
        if (hasReferenceDistanceBug()) {
            AL10.alSourcef(sourceID, 4128, maxDistance / 2.0F);
        }
    }

    public static boolean hasReferenceDistanceBug() {
        if (referenceDistanceBug == null) {
            String version = AL10.alGetString(45058);
            referenceDistanceBug = version != null && REFERENCE_DISTANCE_BUG_VERSION.matcher(version).find();
        }

        return referenceDistanceBug;
    }

    public static void setLastSoundCategoryAndName(SoundSource sc, Identifier id) {
        lastSoundCategory = sc;
        lastSound = id;
    }

    public static void onPlaySound(double posX, double posY, double posZ, int sourceID) {
        processSound(sourceID, posX, posY, posZ, lastSoundCategory, lastSound, false);
    }

    public static void onPlayReverb(double posX, double posY, double posZ, int sourceID) {
        processSound(sourceID, posX, posY, posZ, lastSoundCategory, lastSound, true);
    }

    public static Vec3 processSound(int source, double posX, double posY, double posZ, SoundSource category, Identifier sound) {
        return processSound(source, posX, posY, posZ, category, sound, false);
    }

    @Nullable
    public static Vec3 processSound(int source, double posX, double posY, double posZ, SoundSource category, Identifier sound, boolean auxOnly) {
        return processSound(source, posX, posY, posZ, category, sound, auxOnly ? 0.0F : 1.0F);
    }

    @Nullable
    public static Vec3 processSound(int source, double posX, double posY, double posZ, SoundSource category, Identifier sound, float directGainScale) {
        if (!SoundPhysicsMod.CONFIG.enabled.get()) {
            return null;
        }

        Loggers.logDebug(
            "Playing sound with source id '{}', position x:{}, y:{}, z:{}, \tcategory: '{}' \tname: '{}'", source, posX, posY, posZ, category.toString(), sound
        );
        TaskProfiler.TaskProfilerHandle profile = profiler.profile();
        Vec3 newPos = evaluateEnvironment(source, posX, posY, posZ, category, sound, directGainScale);
        profile.finish();
        Loggers.logProfiling("Evaluated environment for sound {} in {} ms", sound, profile.getDuration());
        profiler.onTally(() -> profiler.logResults());
        return newPos;
    }

    @Nullable
    private static Vec3 evaluateEnvironment(int sourceID, double posX, double posY, double posZ, SoundSource category, Identifier sound, float directGainScale) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player != null && level != null && (posX != 0.0 || posY != 0.0 || posZ != 0.0)) {
            Vec3 soundPos = new Vec3(posX, posY, posZ);
            double distance = player.position().distanceTo(soundPos);
            if (distance > SoundPhysicsMod.CONFIG.maxSoundProcessingDistance.get()) {
                Loggers.logDebug("Sound {} is too far away from player ({} blocks)", sound, distance);
                setDefaultEnvironment(sourceID, directGainScale);
                return null;
            }

            if (!SoundPhysicsMod.CONFIG.updateMovingSounds.get() && category == SoundSource.RECORDS) {
                setDefaultEnvironment(sourceID, directGainScale);
                return null;
            }

            if (!SoundRateManager.isWorldInitialized()) {
                Loggers.logDebug("Sound {} skipped because the world is not initialized yet", sound);
                setDefaultEnvironment(sourceID, directGainScale);
                return null;
            }

            if (SoundRateManager.incrementAndCheckLimit(sound)) {
                Loggers.logDebug("Sound {} skipped due to sound rate limit", sound);
                setDefaultEnvironment(sourceID, directGainScale);
                return null;
            }

            if (!SoundPhysicsMod.CONFIG.evaluateAmbientSounds.get() && isAmbientSound(sound)) {
                Loggers.logDebug("Sound {} skipped due to ambient sound evaluation option", sound);
                setDefaultEnvironment(sourceID, directGainScale);
                return null;
            }

            float absorptionCoeff = (float)(SoundPhysicsMod.CONFIG.blockAbsorption.get().floatValue() * 3.0);
            Vec3 playerPos = minecraft.gameRenderer.getMainCamera().position();
            Vec3 normalToPlayer = playerPos.subtract(soundPos).normalize();
            BlockPos soundBlockPos = BlockPos.containing(soundPos);
            FluidState soundFluidState = getLevelProxy().getFluidState(soundBlockPos);
            boolean sourceIsUnderwater = soundFluidState.is(FluidTags.WATER);
            Loggers.logDebug(
                "Player pos: {}, {}, {} \tSound Pos: {}, {}, {} \tTo player vector: {}, {}, {}",
                playerPos.x,
                playerPos.y,
                playerPos.z,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                normalToPlayer.x,
                normalToPlayer.y,
                normalToPlayer.z
            );
            double occlusionAccumulation = calculateOcclusion(soundPos, playerPos, category, sound);
            float directCutoff = (float)Math.exp(-occlusionAccumulation * absorptionCoeff);
            float directGain = directGainScale * (float)Math.pow(directCutoff, 0.1);
            Loggers.logOcclusion("Direct cutoff: {}, direct gain: {}", directCutoff, directGain);
            float sendGain0 = 0.0F;
            float sendGain1 = 0.0F;
            float sendGain2 = 0.0F;
            float sendGain3 = 0.0F;
            float sendCutoff0 = 1.0F;
            float sendCutoff1 = 1.0F;
            float sendCutoff2 = 1.0F;
            float sendCutoff3 = 1.0F;
            if (minecraft.player.isUnderWater() || sourceIsUnderwater) {
                directCutoff *= 1.0F - SoundPhysicsMod.CONFIG.underwaterFilter.get();
            }

            float maxDistance = 256.0F;
            int numRays = SoundPhysicsMod.CONFIG.environmentEvaluationRayCount.get();
            int rayBounces = SoundPhysicsMod.CONFIG.environmentEvaluationRayBounces.get();
            ReflectedAudio audioDirection = new ReflectedAudio(occlusionAccumulation, sound);
            float[] bounceReflectivityRatio = new float[rayBounces];
            float rcpTotalRays = 1.0F / (numRays * rayBounces);
            float gAngle = 10.166408F;
            Vec3 directSharedAirspaceVector = getSharedAirspace(soundPos, playerPos);
            if (directSharedAirspaceVector != null) {
                audioDirection.addDirectAirspace(directSharedAirspaceVector);
            }

            for (int i = 0; i < numRays; i++) {
                float fiN = (float)i / numRays;
                float longitude = gAngle * i * 1.0F;
                float latitude = (float)Math.asin(fiN * 2.0F - 1.0F);
                Vec3 rayDir = new Vec3(Math.cos(latitude) * Math.cos(longitude), Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude));
                Vec3 rayEnd = new Vec3(soundPos.x + rayDir.x * maxDistance, soundPos.y + rayDir.y * maxDistance, soundPos.z + rayDir.z * maxDistance);
                BlockHitResult rayHit = RaycastUtils.rayCast(getLevelProxy(), soundPos, rayEnd, soundBlockPos);
                if (rayHit.getType() == Type.BLOCK) {
                    double rayLength = soundPos.distanceTo(rayHit.getLocation());
                    BlockPos lastHitBlock = rayHit.getBlockPos();
                    Vec3 lastHitPos = rayHit.getLocation();
                    Vec3 lastHitNormal = new Vec3(rayHit.getDirection().step());
                    Vec3 lastRayDir = rayDir;
                    float totalRayDistance = (float)rayLength;
                    RaycastRenderer.addSoundBounceRay(soundPos, rayHit.getLocation(), RaycastRenderer.color(ChatFormatting.GREEN));
                    Vec3 firstSharedAirspaceVector = getSharedAirspace(rayHit, playerPos);
                    if (firstSharedAirspaceVector != null) {
                        audioDirection.addSharedAirspace(firstSharedAirspaceVector, totalRayDistance);
                    }

                    for (int j = 0; j < rayBounces; j++) {
                        Vec3 newRayDir = reflect(lastRayDir, lastHitNormal);
                        Vec3 newRayStart = lastHitPos;
                        Vec3 newRayEnd = new Vec3(
                            newRayStart.x + newRayDir.x * maxDistance, newRayStart.y + newRayDir.y * maxDistance, newRayStart.z + newRayDir.z * maxDistance
                        );
                        BlockHitResult newRayHit = RaycastUtils.rayCast(getLevelProxy(), newRayStart, newRayEnd, lastHitBlock);
                        float blockReflectivity = getBlockReflectivity(lastHitBlock);
                        float energyTowardsPlayer = 0.25F * (blockReflectivity * 0.75F + 0.25F);
                        if (newRayHit.getType() == Type.MISS) {
                            totalRayDistance = (float)(totalRayDistance + lastHitPos.distanceTo(playerPos));
                            RaycastRenderer.addSoundBounceRay(newRayStart, newRayEnd, RaycastRenderer.color(ChatFormatting.RED));
                        } else {
                            Vec3 newRayHitPos = newRayHit.getLocation();
                            RaycastRenderer.addSoundBounceRay(newRayStart, newRayHitPos, RaycastRenderer.color(ChatFormatting.BLUE));
                            double newRayLength = lastHitPos.distanceTo(newRayHitPos);
                            bounceReflectivityRatio[j] += blockReflectivity;
                            totalRayDistance = (float)(totalRayDistance + newRayLength);
                            lastHitPos = newRayHitPos;
                            lastHitNormal = new Vec3(newRayHit.getDirection().step());
                            lastRayDir = newRayDir;
                            lastHitBlock = newRayHit.getBlockPos();
                            Vec3 sharedAirspaceVector = getSharedAirspace(newRayHit, playerPos);
                            if (sharedAirspaceVector != null) {
                                audioDirection.addSharedAirspace(sharedAirspaceVector, totalRayDistance);
                            }
                        }

                        if (!(totalRayDistance < SoundPhysicsMod.CONFIG.reverbAttenuationDistance.get())) {
                            float reflectionDelay = (float)Math.max(totalRayDistance, 0.0) * 0.12F * blockReflectivity;
                            float cross0 = 1.0F - Mth.clamp(Math.abs(reflectionDelay - 0.0F), 0.0F, 1.0F);
                            float cross1 = 1.0F - Mth.clamp(Math.abs(reflectionDelay - 1.0F), 0.0F, 1.0F);
                            float cross2 = 1.0F - Mth.clamp(Math.abs(reflectionDelay - 2.0F), 0.0F, 1.0F);
                            float cross3 = Mth.clamp(reflectionDelay - 2.0F, 0.0F, 1.0F);
                            sendGain0 += cross0 * energyTowardsPlayer * 6.4F * rcpTotalRays;
                            sendGain1 += cross1 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                            sendGain2 += cross2 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                            sendGain3 += cross3 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                            if (newRayHit.getType() == Type.MISS) {
                                break;
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < bounceReflectivityRatio.length; i++) {
                bounceReflectivityRatio[i] /= numRays;
                Loggers.logEnvironment("Bounce reflectivity {}: {}", i, bounceReflectivityRatio[i]);
            }

            Vec3 newSoundPos = audioDirection.evaluateSoundPosition(soundPos, playerPos);
            if (newSoundPos != null) {
                setSoundPos(sourceID, newSoundPos);
                soundPos = newSoundPos;
            }

            float sharedAirspace = audioDirection.getSharedAirspaces() * 64.0F * rcpTotalRays;
            Loggers.logEnvironment("Shared airspace: {} ({})", sharedAirspace, audioDirection.getSharedAirspaces());
            float sharedAirspaceWeight0 = Mth.clamp(sharedAirspace / 20.0F, 0.0F, 1.0F);
            float sharedAirspaceWeight1 = Mth.clamp(sharedAirspace / 15.0F, 0.0F, 1.0F);
            float sharedAirspaceWeight2 = Mth.clamp(sharedAirspace / 10.0F, 0.0F, 1.0F);
            float sharedAirspaceWeight3 = Mth.clamp(sharedAirspace / 10.0F, 0.0F, 1.0F);
            sendCutoff0 = (float)Math.exp(-occlusionAccumulation * absorptionCoeff * 1.0) * (1.0F - sharedAirspaceWeight0) + sharedAirspaceWeight0;
            sendCutoff1 = (float)Math.exp(-occlusionAccumulation * absorptionCoeff * 1.0) * (1.0F - sharedAirspaceWeight1) + sharedAirspaceWeight1;
            sendCutoff2 = (float)Math.exp(-occlusionAccumulation * absorptionCoeff * 1.0) * (1.0F - sharedAirspaceWeight2) + sharedAirspaceWeight2;
            sendCutoff3 = (float)Math.exp(-occlusionAccumulation * absorptionCoeff * 1.0) * (1.0F - sharedAirspaceWeight3) + sharedAirspaceWeight3;
            float averageSharedAirspace = (sharedAirspaceWeight0 + sharedAirspaceWeight1 + sharedAirspaceWeight2 + sharedAirspaceWeight3) * 0.25F;
            directCutoff = Math.max((float)Math.pow(averageSharedAirspace, 0.5) * 0.2F, directCutoff);
            directGain = directGainScale * (float)Math.pow(directCutoff, 0.1);
            sendGain1 *= bounceReflectivityRatio[1];
            if (bounceReflectivityRatio.length > 2) {
                sendGain2 *= (float)Math.pow(bounceReflectivityRatio[2], 3.0);
            }

            if (bounceReflectivityRatio.length > 3) {
                sendGain3 *= (float)Math.pow(bounceReflectivityRatio[3], 4.0);
            }

            sendGain0 = Mth.clamp(sendGain0, 0.0F, 1.0F);
            sendGain1 = Mth.clamp(sendGain1, 0.0F, 1.0F);
            sendGain2 = Mth.clamp(sendGain2 * 1.05F - 0.05F, 0.0F, 1.0F);
            sendGain3 = Mth.clamp(sendGain3 * 1.05F - 0.05F, 0.0F, 1.0F);
            sendGain0 *= (float)Math.pow(sendCutoff0, 0.1);
            sendGain1 *= (float)Math.pow(sendCutoff1, 0.1);
            sendGain2 *= (float)Math.pow(sendCutoff2, 0.1);
            sendGain3 *= (float)Math.pow(sendCutoff3, 0.1);
            float soundDistance = (float)playerPos.distanceTo(soundPos);
            float maxSoundDistance = AL10.alGetSourcef(sourceID, 4131);
            float sendGainMultiplier = 1.0F - Math.min(soundDistance / (maxSoundDistance * SoundPhysicsMod.CONFIG.reverbDistance.get()), 1.0F);
            sendGain0 = sendGainMultiplier * sendGain0;
            sendGain1 = sendGainMultiplier * sendGain1;
            sendGain2 = sendGainMultiplier * sendGain2;
            sendGain3 = sendGainMultiplier * sendGain3;
            Loggers.logEnvironment("Final environment settings: {}, {}, {}, {}", sendGain0, sendGain1, sendGain2, sendGain3);
            assert minecraft.player != null;
            if (minecraft.player.isUnderWater() || sourceIsUnderwater) {
                sendCutoff0 *= 0.4F;
                sendCutoff1 *= 0.4F;
                sendCutoff2 *= 0.4F;
                sendCutoff3 *= 0.4F;
            }

            setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain);
            return newSoundPos;
        } else {
            setDefaultEnvironment(sourceID, directGainScale);
            return null;
        }
    }

    public static boolean isAmbientSound(Identifier sound) {
        return AMBIENT_PATTERN.matcher(sound.toString()).matches();
    }

    private static float getBlockReflectivity(BlockPos blockPos) {
        ClientLevelProxy levelProxy = getLevelProxy();
        if (levelProxy == null) {
            return SoundPhysicsMod.CONFIG.defaultBlockReflectivity.get();
        }

        BlockState blockState = levelProxy.getBlockState(blockPos);
        return SoundPhysicsMod.REFLECTIVITY_CONFIG.getBlockDefinitionValue(blockState);
    }

    private static Vec3 reflect(Vec3 dir, Vec3 normal) {
        double dot = dir.dot(normal) * 2.0;
        double x = dir.x - dot * normal.x;
        double y = dir.y - dot * normal.y;
        double z = dir.z - dot * normal.z;
        return new Vec3(x, y, z);
    }

    private static double calculateOcclusion(Vec3 soundPos, Vec3 playerPos, SoundSource category, Identifier sound) {
        if (SoundPhysicsMod.CONFIG.strictOcclusion.get()) {
            return Math.min(runOcclusion(soundPos, playerPos), SoundPhysicsMod.CONFIG.maxOcclusion.get().floatValue());
        }

        boolean isBlock = category == SoundSource.BLOCKS || BLOCK_PATTERN.matcher(sound.toString()).matches();
        double variationFactor = SoundPhysicsMod.CONFIG.occlusionVariation.get().floatValue();
        if (isBlock) {
            variationFactor = Math.max(variationFactor, 0.49);
        }

        double occlusionAccMin = Double.MAX_VALUE;
        occlusionAccMin = Math.min(occlusionAccMin, runOcclusion(soundPos, playerPos));
        if (variationFactor > 0.0) {
            for (int x = -1; x <= 1; x += 2) {
                for (int y = -1; y <= 1; y += 2) {
                    for (int z = -1; z <= 1; z += 2) {
                        Vec3 offset = new Vec3(x, y, z).scale(variationFactor);
                        occlusionAccMin = Math.min(occlusionAccMin, runOcclusion(soundPos.add(offset), playerPos.add(offset)));
                    }
                }
            }
        }

        return Math.min(occlusionAccMin, SoundPhysicsMod.CONFIG.maxOcclusion.get().floatValue());
    }

    private static double runOcclusion(Vec3 soundPos, Vec3 playerPos) {
        ClientLevelProxy levelProxy = getLevelProxy();
        if (levelProxy == null) {
            return 0.0;
        }

        double occlusionAccumulation = 0.0;
        Vec3 rayOrigin = soundPos;
        BlockPos lastBlockPos = BlockPos.containing(soundPos);

        for (int i = 0; i < SoundPhysicsMod.CONFIG.maxOcclusionRays.get(); i++) {
            BlockHitResult rayHit = RaycastUtils.rayCast(getLevelProxy(), rayOrigin, playerPos, lastBlockPos);
            lastBlockPos = rayHit.getBlockPos();
            if (rayHit.getType() == Type.MISS) {
                RaycastRenderer.addOcclusionRay(
                    rayOrigin,
                    playerPos.add(0.0, -0.1, 0.0),
                    Mth.hsvToArgb(0.33333334F * (1.0F - Math.min(1.0F, (float)occlusionAccumulation / 12.0F)), 1.0F, 1.0F, 255)
                );
                break;
            }

            RaycastRenderer.addOcclusionRay(
                rayOrigin, rayHit.getLocation(), Mth.hsvToArgb(0.33333334F * (1.0F - Math.min(1.0F, (float)occlusionAccumulation / 12.0F)), 1.0F, 1.0F, 255)
            );
            BlockPos blockHitPos = rayHit.getBlockPos();
            rayOrigin = rayHit.getLocation();
            BlockState blockHit = levelProxy.getBlockState(blockHitPos);
            float blockOcclusion = SoundPhysicsMod.OCCLUSION_CONFIG.getBlockDefinitionValue(blockHit);
            Vec3 dirVec = rayOrigin.subtract(blockHitPos.getX() + 0.5, blockHitPos.getY() + 0.5, blockHitPos.getZ() + 0.5);
            Direction sideHit = Direction.getApproximateNearest(dirVec.x, dirVec.y, dirVec.z);
            if (!blockHit.isFaceSturdy(levelProxy, rayHit.getBlockPos(), sideHit)) {
                blockOcclusion *= SoundPhysicsMod.CONFIG.nonFullBlockOcclusionFactor.get();
            }

            Loggers.logOcclusion("{} \t{},{},{}", blockHit.getBlock().getDescriptionId(), rayOrigin.x, rayOrigin.y, rayOrigin.z);
            occlusionAccumulation += blockOcclusion;
            if (occlusionAccumulation > SoundPhysicsMod.CONFIG.maxOcclusion.get().floatValue()) {
                Loggers.logOcclusion("Max occlusion reached after {} steps", i + 1);
                break;
            }
        }

        return occlusionAccumulation;
    }

    private static ClientLevelProxy getLevelProxy() {
        return LevelAccessUtils.getClientLevelProxy(minecraft);
    }

    @Nullable
    private static Vec3 getSharedAirspace(BlockHitResult hit, Vec3 listenerPosition) {
        Vector3f hitNormal = hit.getDirection().step();
        Vec3 rayStart = new Vec3(
            hit.getLocation().x + hitNormal.x() * 0.001, hit.getLocation().y + hitNormal.y() * 0.001, hit.getLocation().z + hitNormal.z() * 0.001
        );
        return getSharedAirspace(rayStart, listenerPosition);
    }

    @Nullable
    private static Vec3 getSharedAirspace(Vec3 soundPosition, Vec3 listenerPosition) {
        BlockHitResult finalRayHit = RaycastUtils.rayCast(getLevelProxy(), soundPosition, listenerPosition, null);
        if (finalRayHit.getType() == Type.MISS) {
            RaycastRenderer.addSoundBounceRay(soundPosition, listenerPosition.add(0.0, -0.1, 0.0), RaycastRenderer.color(ChatFormatting.WHITE));
            return soundPosition.subtract(listenerPosition);
        } else {
            return null;
        }
    }

    public static void setDefaultEnvironment(int sourceID) {
        setDefaultEnvironment(sourceID, 1.0F);
    }

    public static void setDefaultEnvironment(int sourceID, float directGain) {
        setEnvironment(sourceID, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, directGain);
    }

    public static void setEnvironment(
        int sourceID,
        float sendGain0,
        float sendGain1,
        float sendGain2,
        float sendGain3,
        float sendCutoff0,
        float sendCutoff1,
        float sendCutoff2,
        float sendCutoff3,
        float directCutoff,
        float directGain
    ) {
        if (SoundPhysicsMod.CONFIG.enabled.get()) {
            if (maxAuxSends >= 4) {
                EXTEfx.alFilterf(sendFilter0, 1, sendGain0);
                EXTEfx.alFilterf(sendFilter0, 2, sendCutoff0);
                AL11.alSource3i(sourceID, 131078, auxFXSlot0, 3, sendFilter0);
                Loggers.logALError("Set environment filter0:");
            }

            if (maxAuxSends >= 3) {
                EXTEfx.alFilterf(sendFilter1, 1, sendGain1);
                EXTEfx.alFilterf(sendFilter1, 2, sendCutoff1);
                AL11.alSource3i(sourceID, 131078, auxFXSlot1, 2, sendFilter1);
                Loggers.logALError("Set environment filter1:");
            }

            if (maxAuxSends >= 2) {
                EXTEfx.alFilterf(sendFilter2, 1, sendGain2);
                EXTEfx.alFilterf(sendFilter2, 2, sendCutoff2);
                AL11.alSource3i(sourceID, 131078, auxFXSlot2, 1, sendFilter2);
                Loggers.logALError("Set environment filter2:");
            }

            if (maxAuxSends >= 1) {
                EXTEfx.alFilterf(sendFilter3, 1, sendGain3);
                EXTEfx.alFilterf(sendFilter3, 2, sendCutoff3);
                AL11.alSource3i(sourceID, 131078, auxFXSlot3, 0, sendFilter3);
                Loggers.logALError("Set environment filter3:");
            }

            EXTEfx.alFilterf(directFilter0, 1, directGain);
            EXTEfx.alFilterf(directFilter0, 2, directCutoff);
            AL11.alSourcei(sourceID, 131077, directFilter0);
            Loggers.logALError("Set environment directFilter0:");
            AL11.alSourcef(sourceID, 131079, SoundPhysicsMod.CONFIG.airAbsorption.get());
            Loggers.logALError("Set environment airAbsorption:");
        }
    }

    private static void setSoundPos(int sourceID, Vec3 pos) {
        AL11.alSource3f(sourceID, 4100, (float)pos.x, (float)pos.y, (float)pos.z);
    }

    protected static void setReverbParams(ReverbParams r, int auxFXSlot, int reverbSlot) {
        EXTEfx.alEffectf(reverbSlot, 1, r.density);
        Loggers.logALError("Error while assigning reverb density: " + r.density);
        EXTEfx.alEffectf(reverbSlot, 2, r.diffusion);
        Loggers.logALError("Error while assigning reverb diffusion: " + r.diffusion);
        EXTEfx.alEffectf(reverbSlot, 3, r.gain);
        Loggers.logALError("Error while assigning reverb gain: " + r.gain);
        EXTEfx.alEffectf(reverbSlot, 4, r.gainHF);
        Loggers.logALError("Error while assigning reverb gainHF: " + r.gainHF);
        EXTEfx.alEffectf(reverbSlot, 6, r.decayTime);
        Loggers.logALError("Error while assigning reverb decayTime: " + r.decayTime);
        EXTEfx.alEffectf(reverbSlot, 7, r.decayHFRatio);
        Loggers.logALError("Error while assigning reverb decayHFRatio: " + r.decayHFRatio);
        EXTEfx.alEffectf(reverbSlot, 9, r.reflectionsGain);
        Loggers.logALError("Error while assigning reverb reflectionsGain: " + r.reflectionsGain);
        EXTEfx.alEffectf(reverbSlot, 12, r.lateReverbGain);
        Loggers.logALError("Error while assigning reverb lateReverbGain: " + r.lateReverbGain);
        EXTEfx.alEffectf(reverbSlot, 13, r.lateReverbDelay);
        Loggers.logALError("Error while assigning reverb lateReverbDelay: " + r.lateReverbDelay);
        EXTEfx.alEffectf(reverbSlot, 19, r.airAbsorptionGainHF);
        Loggers.logALError("Error while assigning reverb airAbsorptionGainHF: " + r.airAbsorptionGainHF);
        EXTEfx.alEffectf(reverbSlot, 22, r.roomRolloffFactor);
        Loggers.logALError("Error while assigning reverb roomRolloffFactor: " + r.roomRolloffFactor);
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, 1, reverbSlot);
    }
}
