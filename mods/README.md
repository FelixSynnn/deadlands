# Bundled mods

Each folder is a separate Fabric mod, built with DEADLANDS and bundled inside the DEADLANDS jar
(jar-in-jar; see the root `build.gradle`). Each keeps its own packages, metadata, credits and
licence. They are not part of DEADLANDS' All-Rights-Reserved code.

| Folder | Mod | Authors (from its `fabric.mod.json`) | Licence | Source |
|--------|-----|--------------------------------------|---------|--------|
| `smooth-player-animations` | SmoothPlayerAnimations 2.1.0 | the project owner | All rights reserved (the project owner's own mod) | Decompiled project supplied by the owner |
| `farmers-delight` | Farmer's Delight Refabricated 3.6.16 | MehVahdJukaar, ChrysanthCow, cassiancc | MIT (`farmers-delight/LICENSE`) | Decompiled project supplied by the owner |
| `sound-physics-remastered` | Sound Physics Remastered 1.5.2 | Sonic Ether, vlad2305m, Max Henkel, Saint | GPL (metadata says "GPL"; no LICENSE file was in the supplied project) | Decompiled project supplied by the owner |
| `enhanced-visuals` | EnhancedVisuals 1.8.27 | CreativeMD, AriaFreeze | LGPL-3.0-only (`enhanced-visuals/LICENSE`) | Decompiled from `EnhancedVisuals-original.jar` (Vineflower 1.12.0, after Loom remapped it to Mojang names) |

CreativeCore 2.14.11 (CreativeMD, LGPL-3.0) is bundled unchanged from Modrinth because
EnhancedVisuals needs it.

## Changes made to bring them in

- Every subproject: the Loom plugin version is taken from the root build (same 1.19.3), and the
  `processResources` version expansion reads the version at configuration time (the root build uses
  Gradle's configuration cache).
- `enhanced-visuals` (decompiled, so a few lines needed fixing to compile):
  - `mixin/PlayerMixin.java`, `mixin/ProjectileMixin.java`: `(Player)this` and `(Projectile)this` became
    `(Player)(Object)this` and `(Projectile)(Object)this` (the decompiler dropped the `Object` step).
  - The Survive, Tough As Nails and Cold Sweat integrations (`common/addon/`) are NeoForge-only: they
    stay on disk but are excluded from this Fabric build, and the three calls that loaded them when
    those mods were present are commented out in `EnhancedVisuals.java`.
  - It compiles against `net.neoforged:bus:7.2.0`, the event bus CreativeCore already ships.
