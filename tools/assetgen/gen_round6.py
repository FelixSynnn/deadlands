"""Phase 5, sixth round: text and sounds for hiding in the dark, terror radii, the Aberrant's lunge, the Green
Tornado, safe respawn and the spawn settings.

The Green Tornado's cues reuse the mod's Pixabay recordings (credited) at other pitches, so they are told apart:
its warning is the siren, lower and slower; its roar is the twister's, deeper.
"""
from __future__ import annotations

import json

import dl_data as D
import dl_noise as N

NS = "deadlands"

TEXT = {
    # Hiding in the dark.
    "config.deadlands.section.stealth": "Hiding in the Dark",
    "config.deadlands.stealth.enabled": "Hiding in the dark",
    "config.deadlands.stealth.darkRangeFactor": "Sight range in darkness",
    "config.deadlands.stealth.crouchFactor": "Crouching sight multiplier",
    "config.deadlands.stealth.darkLight": "Darkest light that hides you",
    "config.deadlands.stealth.loseSeconds": "Seconds out of sight to lose a chaser",
    "config.deadlands.stealth.searchSeconds": "Seconds a creature searches",
    "config.deadlands.stealth.attackRevealSeconds": "Seconds an attack gives you away",
    # Terror radii.
    "config.deadlands.audio.aberrantTerrorRadius": "Aberrant terror radius",
    "config.deadlands.audio.guestTerrorRadius": "Guest terror radius",
    # The Aberrant's lunge.
    "config.deadlands.asesino.lungeCooldownSeconds": "Lunge cooldown (seconds)",
    "config.deadlands.asesino.lungeDamageFactor": "Lunge damage (share of slash)",
    # Safe respawn.
    "config.deadlands.survival.safeRespawn": "Safe respawn",
    "config.deadlands.survival.safeRespawnRadius": "Safe respawn radius",
    # Spawning.
    "config.deadlands.zombies.spawnRateFactor": "Spawn amount",
    "config.deadlands.zombies.groupSizeFactor": "Group size",
    "config.deadlands.zombies.daytimeOutdoorCap": "Daytime cap in the open",
    # The Green Tornado.
    "config.deadlands.disasters.greenTornado": "Green Tornado",
    "config.deadlands.disasters.greenTornadoWeight": "Green Tornado rarity weight",
    "config.deadlands.disasters.greenTornadoCharacter": "Green Tornado character",
    "disaster.deadlands.green_tornado": "Green Tornado",
    "disaster.deadlands.green_tornado.title": "Green Tornado",
    "disaster.deadlands.green_tornado.warning": "The sky has turned the colour of a bruise, and the wind smells of the Kessler zone. A green tornado is coming. Get inside and stay away from it.",
    "disaster.deadlands.green_tornado.start": "The Green Tornado is on the ground! Don't breathe it in!",
    "subtitles.deadlands.disaster.green_tornado.warning": "Green Tornado warning",
    "subtitles.deadlands.disaster.green_tornado": "Green Tornado",
}

SOUNDS = {
    "disaster.green_tornado.warning": {"subtitle": f"subtitles.{NS}.disaster.green_tornado.warning",
                                       "sounds": [{"name": f"{NS}:pixabay/warning_siren", "pitch": 0.72}]},
    "disaster.green_tornado": {"subtitle": f"subtitles.{NS}.disaster.green_tornado",
                               "sounds": [{"name": f"{NS}:pixabay/twister", "pitch": 0.78}]},
}


def build() -> None:
    path = N.ASSETS / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    sounds.update(SOUNDS)
    path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    D.merge_lang(TEXT)
    print(f"  round 6: {len(TEXT)} lines of text, {len(SOUNDS)} sound events (the Green Tornado)")


if __name__ == "__main__":
    build()
