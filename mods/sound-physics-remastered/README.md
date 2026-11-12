# Sound Physics Remastered (Fabric 1.21.11) - decompiled

Rebuildable source made by decompiling `sound-physics-remastered-fabric-1.21.11-1.5.2.jar` (Vineflower 1.12.0, Mojang mappings).

- **License:** GPL (declared in `fabric.mod.json`; the jar has no license file). Anything you distribute must stay
  under the GPL with source available. Get the exact license text from the official repo.
- **Authors:** Sonic Ether, vlad2305m, Max Henkel, Saint.
- **Official source:** https://github.com/henkelmax/sound-physics-remastered. That repo has the real source with
  comments and original formatting, so prefer it for real work.

## Build

Gradle needs JDK 25 (Loom 1.19); the mod targets Java 21.

    ./gradlew build

## Changes made so it compiles

- Optional integrations are compile-only: Simple Voice Chat, Cloth Config and ModMenu, plus `jsr305` for
  `@Nullable`.
- The bundled config library (`de.maxhenkel.sound_physics_remastered.configbuilder`) was decompiled with the mod.
- Decompiler fix: raw casts in `ClothConfigIntegration.fromConfigEntry`.
- Compared with the original jar: two empty synthetic `$1` classes and some `access$` helpers are gone. The bundled
  library was originally compiled for older Java, which needs them; Java 21 uses nestmates instead. No real members differ.
