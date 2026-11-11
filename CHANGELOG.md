# DEADLANDS changelog

## 1.0.0: atmosphere, audio, texture and stability overhaul

Everything below is original: synthesised or painted by the generators in `tools/assetgen` (no
samples, no third-party art). Regenerate with `python tools/assetgen/build_all.py` (see that file
for the group order). Verified by the automated tests listed at the end.

### Critical fix: Stalker crash

- **Fixed** `ClassCastException: StalkerEntity cannot be cast to class_8153` (the client crashed on
  `clientbound/minecraft:entity_event` when a Stalker pounced). Root cause: the pounce was sent as
  entity event **63**, which vanilla's `ClientPacketListener.handleEntityEvent` handles itself as the
  Sniffer's digging sound, casting the entity to `Sniffer` before the entity ever sees the event.
- The slam (62), scream (64) and emerge (66) events also reused vanilla ids (Warden sonic charge,
  Armadillo peek, shake): not intercepted today, but fragile. **All** DEADLANDS entity events now
  live in their own range (100+) in `entity/DLEntityEvents`, documented there.
- Regression tests: a server game test reads every id vanilla declares (by reflection, so a future
  Minecraft update that adds ids is caught) and fails if a DEADLANDS id overlaps; a client scene
  sends every infected event over the network to real client-side Stalkers, provokes a real
  AI-driven pounce (animation and sound checked on the client), and spawns, signals and removes ten
  Stalkers five times over.

### Other bugs fixed

- **Ambient sounds were mostly silent.** Minecraft fades a positioned sound to nothing at
  16 blocks x volume, and the location ambience placed its sounds 10 to 28 blocks away at volume 1
  or less. The new director works out loudness from distance itself (see Soundscape).
- **Biome ambience was far too busy**: the biomes' own additions fired every 4 to 8 seconds
  (crows in farmland every 4.5 s). They are now five times rarer and the crows are left to the
  director, which places them on rooftops and in the distance.
- **Melee weapons were held like loose items** (flat `item/generated` models): they now use the
  handheld model; knives sit smaller in the hand and firearms point ahead.
- **Embers turned almost black at night and in the shade** (muzzle flashes, crash sites, supply-drop
  smoke): they were lit by the world like ash. They are now drawn full-bright, as vanilla flames
  are, from a sprite with a hot core, and cool from orange to red as they rise.
- The menu's corner stamp still read "DEV BUILD - MILESTONE 1": now "BETA · DEVELOPMENT BUILD".
- Client test: the natural-structure screenshots were covered by the new world's introduction
  (now skipped); the opening skip is shared by every scene.

### Soundtrack (new: in-game music did not exist)

- `client/sound/MusicDirector` replaces Minecraft's in-game music (the menus keep the title theme).
  Moods: **exploration** by day, **night** after dark or deep underground, **safe zone** in a
  settlement that tolerates you (the server says so) or in friendly company, **danger** when infected
  hunt you nearby, a boss is close or you are being hurt, **horde** on horde nights.
- Danger is a threat score (hunting infected by kind and distance, bosses, being hurt) that must
  hold for 1.5 s before the music turns and stay low for 12 s before it lets go. Danger cuts in with
  a short fade and loops; quiet moods play one track, then leave one to three minutes of silence
  (shorter with Music Frequency at Frequent, almost none at Constant). Tracks are random but never
  one of the last played for that mood; only one plays at a time apart from a crossfade.
- Follows the Music slider plus new settings: *Audio > DEADLANDS soundtrack in the world*,
  *Soundtrack volume*. A mixin keeps Minecraft's music manager quiet while the director plays.
- **13 original pieces** (`gen_music.py` on the new instrument library `dl_music.py`: modelled
  piano, bowed string ensemble, pads, choir, Karplus-Strong steel guitar, bells, drums, a hall reverb
  and mastering): *Ninety Days* (the new title theme, keeping the first theme's D minor
  Dm - Bb - Gm - A), *Empty Roads*, *What Remains*, *Overgrown*, *Static Lullaby* (exploration),
  *Long Night*, *No Lights*, *Hollow Hours* (night), *Pursuit*, *They Hear You* (danger, seamless
  loops), *Horde*, *Campfire*, *Shelter* (safe). About 27 minutes, 17 MB, streamed.

### Soundscape

- `client/sound/AmbienceDirector` rewritten around **profiles** per kind of place: open country,
  forest, suburb, city, **highway** (on a road outside town), building **interior**, hospital,
  school, industrial, farm, campsite, bunker, underground. Each profile is layers of occasional
  sounds with their own spacing, distances, height and conditions (day, night, dry, wet, storm);
  no two one-shots start within two seconds, at most three play at once, never the same twice in a
  row. Crows call from a roost direction that moves every few minutes.
- One-shots carry no roll-off of their own: their loudness comes from their distance, so a crow
  60 blocks away or a dog streets away is actually heard. Outdoor sounds heard indoors are quieter
  and duller.
- Loops: wind that follows the weather and height (in the trees, between buildings, in the open),
  **rain on the roof** when you are under one (on a metal roof it rings), the hush of a room, the
  hum of a powered building, a transformer outdoors in a powered town.
- New sounds (`gen_soundscape.py`, mono and placed in the world): crows (single calls and flocks
  with wingbeats), far songbirds, leaves, branches, creaking signs, rattling sheet metal, distant
  thunder, distant dogs (and a howl), electrical buzz, wind through broken windows, a building
  settling, and far-off unexplained noises (a door slamming, knocking, dragging, a cry, glass). Seven
  stereo loops and a quiet bed under the main menu (*Audio > Wind and crows on the main menu*).
- *Audio > How often ambient sounds play* (0.25 to 2); changing it takes effect at once.
- The vanilla biomes DEADLANDS keeps (oceans, rivers, beaches, peaks) get murky water and the same
  drab sky and haze as the DEADLANDS biomes.

### The infected's voices

- Every voice regenerated with a new source-filter voice (`dl_voice.py`): a glottal pulse source
  with jitter, shimmer, period doubling (the growl) and creak, gliding vowel formants in a slack
  throat, rasp, a wet gurgle and a throat rattle. Each kind has its own throat (the brute's deep
  chest, the runner's rasp, the screamer's thin wail, the bloater's bubbling, the charred's burnt
  rasp; the stalker only breathes and hisses).
- New per-kind **alert** (a sharp inhale, then a snarl or shriek, the moment one takes a target)
  and **chase** voices (panting snarls while it hunts). The server syncs a new *hunting* flag.
- `client/sound/InfectedVoices`: idle groans share one budget and a crowd thins its own voices out
  (silence stays part of it); chasers speak at their own pace within a shared budget; each infected
  keeps its own pitch with variation per call; at most six voices at once; muffled through walls,
  distant moans far away, breathing up close.
- Old voice files that nothing referred to any more were removed (67 files).

### Main menu

- A new backdrop (`gen_menu.py`): a dead city street at sunset in one-point perspective, painted
  per pixel in world space (walls, fronts and the road are textured in metres, so windows, floors,
  soot, holes, ivy, cracks, faded lines and puddles recede correctly and take the haze of their own
  distance). A broken overpass against the low sun, rooftop trees, utility poles with sagging wires,
  a hanging traffic light, abandoned cars and a bus, rubble, a leaning pole and a dead tree.
- Moves, a little: two fog banks drift across the street and a third lies on the road, light shafts
  breathe, the foreground weeds sway in the wind, and now and then crows cross the sky.
- Layout as before (title upper left, tagline, menu on the left, build stamp upper right, version,
  language, accessibility and copyright at the bottom), all functions unchanged.

### Infected textures

- Redesigned skin (drained, livor mottling, greening, veins, wounds with raw edges, dried blood runs)
  and faces (heavy brow, deep sockets, clouded, hollow or bloodshot eyes, nostrils, hollow cheeks, a
  slack mouth with a few broken teeth, blood about the mouth and chin), with desaturated, darker,
  grimier clothing and blood run down some chests.
- Four new walker outfits: **hospital patient**, **industrial coveralls**, **survivor** (backpack,
  bandaged arm) and **firefighter** (66 looks, was 58; appended, so saved infected keep their look).
- **Per-infected variety**: six wear overlays per rig (blood spatter, a feeding, mud, fresh gashes,
  ash, scratches and bites), one or none chosen from each infected's UUID and drawn over its look,
  plus a slight per-infected skin and cloth tint.

### Weapons

- New: **survival knife**, **hunting knife**, **rusty machete** (wears out sooner), **nail bat**,
  **lead pipe**, **pipe wrench**, with recipes (knife, pipe, nail bat) and loot in workshops,
  toolboxes, hunting lodges, campsites, kitchens, police lockers, military crates, farms and gas
  stations.
- Every weapon icon repainted pixel by pixel (`gen_weapons.py`): bevelled steel with a lit edge,
  scratches, rust, chipped paint, wood grain, wrapped grips, blood. Vanilla iron, diamond and
  netherite swords and axes restyled as worn, tempered and blackened steel.

### Blocks

- **Per-position variants** for the blocks that cover the most ground (`gen_variants.py`): 19
  DEADLANDS blocks (asphalt, concretes, bricks, plaster, planks, metal, sidewalk, rubble, dirt...) and
  10 vanilla ones (stone, cobblestone, gravel, dirt, coarse dirt, sand, mud, mossy cobblestone, oak and
  spruce planks): three more textures each, chosen per block position (with random quarter turns
  where invisible). Made from the finished textures, the outer texels unchanged so any mix tiles.
- **Ten new blocks**: dirty concrete, broken concrete (rebar), potholed asphalt, rotting planks,
  faded planks, corroded metal, industrial panel, dirty window, cracked glass (a stone's impact:
  cracks radiating from one point, joined by broken rings), dusty glass (with a wiped streak).

### Tests

- New client scenes: Stalker regression, audio (menu bed, music director, ambience, rain on metal
  and wooden roofs, danger music, alert and chase voices, safe and night moods), looks (infected
  lineup, close-up, dusk; weapons in the hotbar and in hand; block variants and new blocks; embers
  at night), a crowd of forty hunting infected (frame rate, live heap, and never more than six
  voices at once) and a resource reload (F3+T) in a world.
- Results (2026-11-10): `All 57 required tests passed` (56 DEADLANDS server game tests) and the
  client test passes all 64 checks; forty hunting infected ran at 116 fps on average (lowest
  102) with 25 voices in ten seconds, at most 6 at once, and a live heap of 339 MB before and
  339 MB after; after F3+T the same view looked the same. Details in "Build and test results" in
  DEVELOPMENT_STATUS.md.
