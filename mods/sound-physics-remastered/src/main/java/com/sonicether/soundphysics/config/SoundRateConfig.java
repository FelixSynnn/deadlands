package com.sonicether.soundphysics.config;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.integration.voicechat.AudioChannel;
import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedProperties;
import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class SoundRateConfig extends CommentedPropertyConfig {
    private Map<Identifier, Integer> soundRateConfig;

    public SoundRateConfig(Path path) {
        super(new CommentedProperties(false));
        this.path = path;
        this.reload();
    }

    @Override
    public void load() throws IOException {
        super.load();
        Map<Identifier, Integer> map = this.createDefaultMap();
        Iterator var2 = this.properties.keySet().iterator();

        while (true) {
            String key;
            int value;
            while (true) {
                if (!var2.hasNext()) {
                    this.soundRateConfig = ConfigUtils.sortMap(map);
                    this.saveSync();
                    return;
                }

                key = (String)var2.next();
                String valueString = this.properties.get(key);
                if (valueString != null) {
                    try {
                        value = Integer.parseInt(valueString);
                        break;
                    } catch (NumberFormatException ignored) {
                        try {
                            boolean enabled = Boolean.parseBoolean(valueString);
                            value = enabled ? -1 : 0;
                            break;
                        } catch (Exception e) {
                            Loggers.warn("Failed to set sound rate entry {}", key);
                        }
                    }
                }
            }

            Identifier resourceLocation = Identifier.tryParse(key);
            if (resourceLocation == null) {
                Loggers.warn("Failed to set sound rate entry {}", key);
            } else {
                if (!AudioChannel.isVoicechatSound(resourceLocation)) {
                    this.logIfUnknownSound(resourceLocation);
                }

                map.put(resourceLocation, value);
            }
        }
    }

    private void logIfUnknownSound(Identifier resourceLocation) {
        try {
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(resourceLocation).map(Reference::value).orElse(null);
            if (soundEvent == null) {
                Loggers.log("Unknown sound in sound rate config: {}", resourceLocation);
            }
        } catch (Exception e) {
            Loggers.warn("Failed to parse sound rate entry {}", resourceLocation, e);
        }
    }

    @Override
    public void saveSync() {
        this.properties.clear();
        this.properties.addHeaderComment("Max sounds per tick.");
        this.properties.addHeaderComment("Set to '-1' for an unlimited number of sounds per tick processed.");
        this.properties.addHeaderComment("Set to '0' to disable sound physics for that sound.");
        this.properties.addHeaderComment("Set to '>=1' to configure the maximum number of sounds per tick processed.");
        this.properties.addHeaderComment("This can help prevent lag when some mod or mechanism produces hundreds of sounds per tick.");

        for (Entry<Identifier, Integer> entry : this.soundRateConfig.entrySet()) {
            this.properties.set(entry.getKey().toString(), String.valueOf(entry.getValue()));
        }

        super.saveSync();
    }

    public Map<Identifier, Integer> getSoundRateConfig() {
        return this.soundRateConfig;
    }

    public Integer getMaxCount(Identifier soundEvent) {
        int count = this.soundRateConfig.getOrDefault(soundEvent, -1);
        return count <= -1 ? Integer.MAX_VALUE : count;
    }

    public Map<Identifier, Integer> createDefaultMap() {
        Map<Identifier, Integer> map = new HashMap<>();

        for (SoundEvent event : BuiltInRegistries.SOUND_EVENT) {
            map.put(event.location(), -1);
        }

        map.put(SoundEvents.WEATHER_RAIN.location(), 0);
        map.put(SoundEvents.WEATHER_RAIN_ABOVE.location(), 0);
        map.put(SoundEvents.LIGHTNING_BOLT_THUNDER.location(), 0);
        SoundEvents.GOAT_HORN_SOUND_VARIANTS.forEach(r -> map.put(r.key().identifier(), 0));
        return map;
    }
}
