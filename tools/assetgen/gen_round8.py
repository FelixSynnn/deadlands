"""Eighth round: the setting labels for the main menu's music (client/sound/MenuMusic)."""
from __future__ import annotations

import dl_data as D

TEXT = {
    "config.deadlands.ui.menuMusic": "Main menu music",
    "config.deadlands.ui.menuMusicVolume": "Main menu music volume",
    "config.deadlands.horror.flashlightLightsBlocks": "Flashlight lights up blocks",
}


def build() -> None:
    D.merge_lang(TEXT)
    print(f"  round 8: {len(TEXT)} lines")


if __name__ == "__main__":
    build()
