"""Eighth round: the setting labels for the main menu's music (client/sound/MenuMusic)."""
from __future__ import annotations

import dl_data as D

TEXT = {
    "config.deadlands.ui.menuMusic": "Main menu music",
    "config.deadlands.ui.menuMusicVolume": "Main menu music volume",
    "config.deadlands.horror.flashlightLightsBlocks": "Flashlight lights up blocks",
    "config.deadlands.horror.noSleepRadius": "No sleep with a killer this close",
    "message.deadlands.sleep.killer_near": "You can't sleep. Something is out there.",
    "message.deadlands.sleep.woken": "You jolt awake. Something is close.",
}


def build() -> None:
    D.merge_lang(TEXT)
    print(f"  round 8: {len(TEXT)} lines")


if __name__ == "__main__":
    build()
