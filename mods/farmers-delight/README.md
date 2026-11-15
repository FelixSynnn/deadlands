# Farmer's Delight Refabricated (Fabric 1.21.11) - decompiled

Rebuildable source made by decompiling `FarmersDelight-1.21.11-3.6.16+refabricated.jar` (Vineflower 1.12.0, Mojang mappings).

- **License:** MIT (see `LICENSE`). Keep the copyright and license notice in anything you share.
- **Authors:** MehVahdJukaar, ChrysanthCow, cassiancc (Farmer's Delight by vectorwing).
- **Official source:** https://github.com/MehVahdJukaar/FarmersDelightRefabricated. That repo has the real source with
  comments and original formatting, so prefer it for real work.

## Build

Gradle needs JDK 25 (Loom 1.19); the mod targets Java 21.

    ./gradlew build

## Changes made so it compiles

- The access widener (`farmersdelight.classtweaker`) is wired up through `loom.accessWidenerPath`.
- Optional integrations are compile-only: JEI, REI (plus basic-math and Architectury), RRV, YACL, Cloth Config,
  ModMenu and JSpecify.
- Decompiler fixes: `(Object)this` in mixins, explicit generic types (`BasketBlock`, `YACLCompat`, `ConfigBuilderImpl`),
  `hasItems(new ItemLike[0])` to pick the same overload as the original, and a removed `@Override` on
  `RopeFenceGateBlock.isWall` (the vanilla method is private).
- Compared with the original jar: identical file list. Remaining class differences are compiler-generated only
  (lambda numbering, anonymous-class constructors, member order).
