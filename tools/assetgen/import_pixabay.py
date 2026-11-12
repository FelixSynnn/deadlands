"""Real recordings from Pixabay (Phase 5, step 5; the list the owner approved is docs/PIXABAY_AUDIO_PLAN.md).

Each file is downloaded once into tools/assetgen/_cache/pixabay (skipped when already there), trimmed,
faded, and converted with ffmpeg to OGG Vorbis at 44.1 kHz (mono for sounds heard in the world, stereo
for music, cutscene beds and stings), written to assets/deadlands/sounds/pixabay/, and the sound events
are pointed at them. The synthesised files they replace stay where they are (no longer used).
CREDITS.md at the repository root lists every file: title, author, page and licence.

All files: Pixabay Content License (https://pixabay.com/service/license-summary/).
"""
from __future__ import annotations

import json
import subprocess
import urllib.request
from pathlib import Path

import dl_data as D
import dl_noise as N

NS = "deadlands"
CACHE = N.ROOT / "tools" / "assetgen" / "_cache" / "pixabay"
OUT = N.ASSETS / "sounds" / "pixabay"
LICENSE = "Pixabay Content License"

# name: (title, author, page, download url, channels, seconds kept (None = all), fade out seconds, events)
FILES = {
    "intro_music": ("Apocalypse", "Music_For_Videos", "https://pixabay.com/music/solo-piano-apocalypse-161568/",
                    "https://cdn.pixabay.com/download/audio/2023/08/10/audio_96f5238088.mp3?filename=music_for_videos-apocalypse-161568.mp3",
                    2, 61, 4, ["music.intro"]),
    "intro_ambience": ("Dystopian Winds by Alien I Trust", "Alien_I_Trust", "https://pixabay.com/sound-effects/horror-dystopian-winds-by-alien-i-trust-290058/",
                       "https://cdn.pixabay.com/download/audio/2025/01/20/audio_6c253fd219.mp3?filename=alien_i_trust-dystopian-winds-by-alien-i-trust-290058.mp3",
                       2, 61, 4, ["ambient.intro"]),
    "reunion": ("Relax Emotional Piano ~ 夢の中の再会 ~", "pianocafe_Kumi",
                "https://pixabay.com/music/modern-classical-relax-emotional-piano-%E5%A4%A2%E3%81%AE%E4%B8%AD%E3%81%AE%E5%86%8D%E4%BC%9A-466421/",
                "https://cdn.pixabay.com/download/audio/2026/01/15/audio_95d175125c.mp3?filename=pianocafe_kumi-relax-emotional-piano-466421.mp3",
                2, 36, 4, ["event.reunion"]),
    "warning_siren": ("Civil Defense Siren", "SoundReality", "https://pixabay.com/sound-effects/city-civil-defense-siren-128262/",
                      "https://cdn.pixabay.com/download/audio/2022/12/05/audio_b0137167cf.mp3?filename=soundreality-civil-defense-siren-128262.mp3",
                      1, 12, 3, ["disaster.twister.warning", "disaster.flash_flood.warning", "disaster.tremor.warning", "disaster.skyfall.warning",
                                 "disaster.whiteout.warning", "disaster.dust_wall.warning", "disaster.caustic_rain.warning",
                                 "disaster.static_storm.warning"]),
    "twister": ("Tornado Sound (better quality)", "freesound_community", "https://pixabay.com/sound-effects/film-special-effects-tornado-sound-better-quality-68141/",
                "https://cdn.pixabay.com/download/audio/2022/03/14/audio_1863a31e54.mp3?filename=freesound_community-tornado-sound-better-quality-68141.mp3",
                1, 12, 2, ["disaster.twister"]),
    "flash_flood": ("Flash Flood River", "Alex_Jauk", "https://pixabay.com/sound-effects/nature-flash-flood-river-193062/",
                    "https://cdn.pixabay.com/download/audio/2024/02/26/audio_30e9192c62.mp3?filename=alex_jauk-flash-flood-river-193062.mp3",
                    1, 12, 2, ["disaster.flash_flood"]),
    "tremor": ("Earthquake loop", "freesound_community", "https://pixabay.com/sound-effects/film-special-effects-earthquake-loop-64704/",
               "https://cdn.pixabay.com/download/audio/2022/03/14/audio_ab3ce514fa.mp3?filename=freesound_community-earthquake-loop-64704.mp3",
               1, 12, 2, ["disaster.tremor"]),
    "skyfall": ("meteor flyby", "freesound_community", "https://pixabay.com/sound-effects/film-special-effects-meteor-flyby-14766/",
                "https://cdn.pixabay.com/download/audio/2022/01/18/audio_b6805a282b.mp3?filename=freesound_community-meteor-flyby-14766.mp3",
                1, 10, 2, ["disaster.skyfall"]),
    "whiteout": ("Heavy Blizzard Wind Gust", "DRAGON-STUDIO", "https://pixabay.com/sound-effects/horror-heavy-blizzard-wind-gust-515271/",
                 "https://cdn.pixabay.com/download/audio/2026/04/06/audio_101808be49.mp3?filename=dragon-studio-heavy-blizzard-wind-gust-515271.mp3",
                 1, 12, 2, ["disaster.whiteout"]),
    "dust_wall": ("Sandstorm", "SoundReality", "https://pixabay.com/sound-effects/nature-sandstorm-222741/",
                  "https://cdn.pixabay.com/download/audio/2024/07/07/audio_5411e44741.mp3?filename=soundreality-sandstorm-222741.mp3",
                  1, 12, 2, ["disaster.dust_wall"]),
    "caustic_rain": ("Heavy Rain Outside the Window", "DRAGON-STUDIO", "https://pixabay.com/sound-effects/nature-heavy-rain-outside-the-window-515266/",
                     "https://cdn.pixabay.com/download/audio/2026/04/06/audio_42d88487a7.mp3?filename=dragon-studio-heavy-rain-outside-the-window-515266.mp3",
                     1, 12, 2, ["disaster.caustic_rain"]),
    "caustic_burn": ("sizzle", "freesound_community", "https://pixabay.com/sound-effects/household-sizzle-62476/",
                     "https://cdn.pixabay.com/download/audio/2022/03/14/audio_29bb752b54.mp3?filename=freesound_community-sizzle-62476.mp3",
                     1, 2.5, 0.8, ["disaster.caustic_rain.burn"]),
    "static_storm": ("Thunder Strike", "Universfield", "https://pixabay.com/sound-effects/nature-thunder-strike-124463/",
                     "https://cdn.pixabay.com/download/audio/2022/10/30/audio_65a2d39d7c.mp3?filename=universfield-thunder-strike-124463.mp3",
                     1, None, 1, ["disaster.static_storm"]),
    # melee_swing removed: the recording sounds like a voice saying "whoosh"; weapon.swing keeps gen_weapon_sfx's.
    "melee_hit_blunt": ("Punch 05", "Universfield", "https://pixabay.com/sound-effects/film-special-effects-punch-05-634843/",
                        "https://cdn.pixabay.com/download/audio/2026/11/10/audio_eb7873bf40.mp3?filename=universfield-punch-05-634843.mp3",
                        1, 1.5, 0.3, ["weapon.hit_blunt"]),
    "melee_hit_blade": ("Knife Stab", "Universfield", "https://pixabay.com/sound-effects/film-special-effects-knife-stab-121084/",
                        "https://cdn.pixabay.com/download/audio/2022/09/27/audio_8f017d5236.mp3?filename=universfield-knife-stab-121084.mp3",
                        1, 2, 0.3, ["weapon.hit_blade"]),
    "title_theme": ("Horror Ambient", "leberch", "https://pixabay.com/music/horror-scene-horror-ambient-609124/",
                    "https://cdn.pixabay.com/download/audio/2026/09/23/audio_6c2c2f6d52.mp3?filename=leberch-horror-ambient-609124.mp3",
                    2, None, 3, ["music.title"]),
    "sting_horde": ("Rising Horror Tension", "Universfield", "https://pixabay.com/sound-effects/film-special-effects-rising-horror-tension-130803/",
                    "https://cdn.pixabay.com/download/audio/2022/12/23/audio_005fdac55a.mp3?filename=universfield-rising-horror-tension-130803.mp3",
                    2, None, 1, ["event.horde_start"]),
    "sting_boss": ("Horror Hit", "Universfield", "https://pixabay.com/sound-effects/horror-horror-hit-443980/",
                   "https://cdn.pixabay.com/download/audio/2025/11/28/audio_1d878bbfe5.mp3?filename=universfield-horror-hit-443980.mp3",
                   2, None, 1, ["event.boss"]),
    "sting_day": ("Creepy Piano Stinger", "Universfield", "https://pixabay.com/sound-effects/film-special-effects-creepy-piano-stinger-153296/",
                  "https://cdn.pixabay.com/download/audio/2023/06/11/audio_daaf8a4a94.mp3?filename=universfield-creepy-piano-stinger-153296.mp3",
                  2, None, 0.5, ["event.day_begin"]),
    "sting_town": ("Tense Piano Suspense Stinger", "Emand_Edroff", "https://pixabay.com/sound-effects/musical-tense-piano-suspense-stinger-576272/",
                   "https://cdn.pixabay.com/download/audio/2026/07/29/audio_6ceba1b9ad.mp3?filename=emand_edroff-tense-piano-suspense-stinger-576272.mp3",
                   2, 8, 2, ["event.town_enter"]),
}
STREAM = {"intro_music", "intro_ambience", "reunion", "title_theme"}
SUBTITLES = {"ambient.intro": "Wind howls", "disaster.caustic_rain.burn": "Skin burns"}


def fetch(name: str, url: str) -> Path | None:
    CACHE.mkdir(parents=True, exist_ok=True)
    out = CACHE / f"{name}.mp3"
    if out.exists() and out.stat().st_size > 0:
        return out
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://pixabay.com/"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
    except OSError as failure:
        print(f"    could not download {name}: {failure}")
        return None
    if not data.startswith(b"ID3") and data[:2] != b"\xff\xfb" and data[:1] != b"\xff":
        print(f"    {name}: the download is not an MP3; skipped")
        return None
    out.write_bytes(data)
    return out


def convert(source: Path, name: str, channels: int, keep: float | None, fade: float) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{name}.ogg"
    filters = []
    if keep:
        filters.append(f"atrim=0:{keep}")
        filters.append(f"afade=t=out:st={max(0.0, keep - fade)}:d={fade}")
    filters.append("afade=t=in:d=0.02")
    command = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-af", ",".join(filters),
               "-ar", "44100", "-ac", str(channels), "-c:a", "libvorbis", "-q:a", "5", str(target)]
    subprocess.run(command, check=True)
    return target


def credits() -> None:
    lines = ["# Credits", "", "## Audio from Pixabay", "",
             "These recordings and music tracks come from [Pixabay](https://pixabay.com/) and are used under the",
             "[Pixabay Content License](https://pixabay.com/service/license-summary/). They were converted to OGG Vorbis",
             "and trimmed for the game (`tools/assetgen/import_pixabay.py`).", "",
             "| Used for | Title | Author | Page | License |", "|---|---|---|---|---|"]
    for name, (title, author, page, _url, _ch, _keep, _fade, events) in FILES.items():
        lines.append(f"| `{name}` ({', '.join(events[:2])}{', ...' if len(events) > 2 else ''}) | {title} | {author} | {page} | {LICENSE} |")
    (N.ROOT / "CREDITS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def build() -> None:
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    done = 0
    for name, (title, _author, _page, url, channels, keep, fade, events) in FILES.items():
        source = fetch(name, url)
        if source is None:
            continue
        convert(source, name, channels, keep, fade)
        entry = {"name": f"{NS}:pixabay/{name}"}
        if name in STREAM:
            entry["stream"] = True
        for event in events:
            current = sounds.get(event, {})
            sounds[event] = {**({"subtitle": current["subtitle"]} if "subtitle" in current else {}), "sounds": [entry]}
            if event in SUBTITLES and "subtitle" not in current:
                key = f"subtitles.{NS}.{event}"
                sounds[event] = {"subtitle": key, "sounds": [entry]}
                D.merge_lang({key: SUBTITLES[event]})
        done += 1
    sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    credits()
    print(f"  pixabay: {done} of {len(FILES)} recordings imported")


if __name__ == "__main__":
    build()
