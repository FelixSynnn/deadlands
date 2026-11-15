# Pixabay audio plan (step 5, waiting for approval)

Nothing has been downloaded yet. Each sound has 1 or 2 candidates; my pick is listed first.

- **Format:** every file is converted to OGG Vorbis at 44.1 kHz. Positional sounds are mono; music and cutscene beds are stereo.
- **Credits:** every file is listed in `CREDITS.md`.
- **Files replaced:** the files that get replaced are the placeholders listed in the last column.

## Cutscenes and the reunion

| Sound | Use | Candidates | Replaces |
|---|---|---|---|
| Intro music | "Ninety-Five Days" score, 60 s cut | 1. [Apocalypse](https://pixabay.com/music/solo-piano-apocalypse-161568/) (Music_For_Videos, 1:57)<br>2. [Sad Cinematic](https://pixabay.com/music/orchestral-sad-cinematic-613491/) (AtlasAudio, 1:52) | `music.intro` (now plays What Remains) |
| Intro ambience | Desolate wind bed under the intro | 1. [Dystopian Winds](https://pixabay.com/sound-effects/horror-dystopian-winds-by-alien-i-trust-290058/) (Alien_I_Trust, 1:08)<br>2. [Wind ambience](https://pixabay.com/sound-effects/nature-wind-ambience-14720/) (freesound_community, 2:56) | New: `ambient.intro` |
| Reunion cue | Day 10 meeting, soft swell | 1. [Relax Emotional Piano: 夢の中の再会](https://pixabay.com/music/modern-classical-relax-emotional-piano-%E5%A4%A2%E3%81%AE%E4%B8%AD%E3%81%AE%E5%86%8D%E4%BC%9A-466421/) (pianocafe_Kumi, 1:02; the title means "reunion in a dream")<br>2. [Cinematic Love Music](https://pixabay.com/music/modern-classical-cinematic-love-music-165619/) (Sonican, 2:34) | `event.reunion` (now plays Campfire) |

## Disasters

There is one shared warning siren. Each disaster also has its own sound.

| Sound | Candidates | Replaces |
|---|---|---|
| Warning siren (all disasters) | 1. [Civil Defense Siren](https://pixabay.com/sound-effects/city-civil-defense-siren-128262/) (SoundReality, 0:57)<br>2. [Tornado Siren winding down](https://pixabay.com/sound-effects/film-special-effects-tornado-siren-winding-down-78370/) (freesound_community, 0:58) | `disaster.*.warning` (now gusts and distant rumbles) |
| Twister | 1. [Tornado Sound (better quality)](https://pixabay.com/sound-effects/film-special-effects-tornado-sound-better-quality-68141/) (freesound_community, 0:49)<br>2. [Tornado FX](https://pixabay.com/sound-effects/film-special-effects-tornado-fx-193028/) (SoundReality, 0:24) | `disaster.twister` |
| Flash flood | 1. [Flash Flood River](https://pixabay.com/sound-effects/nature-flash-flood-river-193062/) (Alex_Jauk, 0:21)<br>2. [Flash Flood](https://pixabay.com/sound-effects/nature-flash-flood-202283/) (SoundReality, 0:40) | `disaster.flash_flood` |
| Tremor | 1. [Earthquake loop](https://pixabay.com/sound-effects/film-special-effects-earthquake-loop-64704/) (freesound_community, 0:21)<br>2. [Earthquake Rumble & Cracking](https://pixabay.com/sound-effects/nature-earthquake-rumble-amp-cracking-379298/) (TheMediaGuy, 0:07) | `disaster.tremor` |
| Skyfall | 1. [meteor flyby](https://pixabay.com/sound-effects/film-special-effects-meteor-flyby-14766/) (freesound_community, 0:18)<br>2. [055194_Meteor](https://pixabay.com/sound-effects/film-special-effects-055194-meteor-36671/) (freesound_community, 0:10) | `disaster.skyfall` |
| Whiteout | 1. [Heavy Blizzard Wind Gust](https://pixabay.com/sound-effects/horror-heavy-blizzard-wind-gust-515271/) (DRAGON-STUDIO, 1:32)<br>2. [Wind Artic Cold](https://pixabay.com/sound-effects/nature-wind-artic-cold-6195/) (freesound_community, 0:59) | `disaster.whiteout` |
| Dust wall | 1. [Sandstorm](https://pixabay.com/sound-effects/nature-sandstorm-222741/) (SoundReality, 0:30)<br>2. [Desert wind 2](https://pixabay.com/sound-effects/nature-desert-wind-2-350417/) (TanwerAman, 0:32) | `disaster.dust_wall` |
| Caustic rain (rain) | 1. [Heavy Rain Outside the Window](https://pixabay.com/sound-effects/nature-heavy-rain-outside-the-window-515266/) (DRAGON-STUDIO, 0:41)<br>2. [Heavy Rain: Nature Sounds](https://pixabay.com/sound-effects/heavy-rain-nature-sounds-8186/) (JuliusH, 0:29) | `disaster.caustic_rain` |
| Caustic rain (burn) | 1. [sizzle](https://pixabay.com/sound-effects/household-sizzle-62476/) (freesound_community, 0:16)<br>2. [Acid Bubbling](https://pixabay.com/sound-effects/film-special-effects-acid-bubbling-105397/) (freesound_community, 0:02) | New: `disaster.caustic_rain.burn` |
| Static storm | 1. [Thunder Strike](https://pixabay.com/sound-effects/nature-thunder-strike-124463/) (Universfield, 0:11)<br>2. [Dry Thunder](https://pixabay.com/sound-effects/nature-dry-thunder-443132/) (DRAGON-STUDIO, 0:04) | `disaster.static_storm` |

## Other placeholder sounds

These are the sounds `docs/ASSET_MANIFEST.md` marks as synthesised placeholders.

| Sound | Candidates | Replaces |
|---|---|---|
| Melee swing | 1. [Swing Whoosh Weapon 1](https://pixabay.com/sound-effects/film-special-effects-swing-whoosh-weapon-1-189819/) (floraphonic)<br>2. [Swing Whoosh 5](https://pixabay.com/sound-effects/film-special-effects-swing-whoosh-5-198498/) (floraphonic) | `weapon.swing` (synthesised) |
| Blunt hit | 1. [Punch 05](https://pixabay.com/sound-effects/film-special-effects-punch-05-634843/) (Universfield)<br>2. [Power Punch](https://pixabay.com/sound-effects/film-special-effects-power-punch-192118/) (Universfield) | `weapon.hit_blunt` (synthesised) |
| Blade hit | 1. [Knife Stab](https://pixabay.com/sound-effects/film-special-effects-knife-stab-121084/) (Universfield)<br>2. [Sword Slash With Metallic Impact](https://pixabay.com/sound-effects/film-special-effects-sword-slash-with-metallic-impact-185435/) (DavidDumaisAudio) | `weapon.hit_blade` (synthesised) |
| Menu theme | 1. [Horror Ambient](https://pixabay.com/music/horror-scene-horror-ambient-609124/) (leberch, 2:26)<br>2. [Phantom's Embrace](https://pixabay.com/music/ambient-phantomx27s-embrace-164479/) (moodmode, 1:28) | `music/title_theme.ogg` (synthesised) |
| Stings: day, town, horde, boss | 1. [Rising Horror Tension](https://pixabay.com/sound-effects/film-special-effects-rising-horror-tension-130803/) for horde<br>2. [Horror Hit](https://pixabay.com/sound-effects/horror-horror-hit-443980/) for boss<br>3. [Creepy Piano Stinger](https://pixabay.com/sound-effects/film-special-effects-creepy-piano-stinger-153296/) for day<br>4. [Tense Piano Suspense Stinger](https://pixabay.com/sound-effects/musical-tense-piano-suspense-stinger-576272/) for town | `event/*.ogg` (synthesised) |
