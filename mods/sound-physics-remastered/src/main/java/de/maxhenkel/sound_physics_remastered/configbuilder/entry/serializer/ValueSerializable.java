package de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValueSerializable {
    Class<? extends ValueSerializer<?>> value();
}
