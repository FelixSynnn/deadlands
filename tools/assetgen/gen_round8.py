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
    "message.deadlands.sleep.night": "You can't sleep. Not tonight. You'll have to make it to morning. (Spawn point set.)",
    "config.deadlands.horror.noSleepAtNight": "No sleeping at night",
    # The story so far and the Cinematic Archive (Phase 5 step 6, story/StoryArchive).
    "story.deadlands.archive.header": "The story so far",
    "journal.deadlands.tab.story": "Story and cinematics",
    "story.deadlands.chapter.one": "Chapter One: The Case",
    "story.deadlands.chapter.two": "Chapter Two: Adam's Lab",
    "story.deadlands.chapter.three": "Chapter Three: The House",
    "story.deadlands.chapter.four": "Chapter Four: Pinecrest",
    "story.deadlands.chapter.imlilylol_one": "Imlilylol, Chapter One: Open Road",
    "story.deadlands.chapter.felixsyn_one": "FelixSyn, Chapter One: The East Gate",
    "story.deadlands.status.done": "done",
    "story.deadlands.status.active": "under way",
    "story.deadlands.status.locked": "not yet",
    "story.deadlands.archive.no_ending": "No ending reached yet.",
    "story.deadlands.archive.ending": "Ending reached: %s",
    "ending.deadlands.name.extraction": "Extraction",
    "ending.deadlands.name.rescue": "Rescue",
    "ending.deadlands.name.stay": "Staying Behind",
    "ending.deadlands.name.patient_zero": "Patient Zero",
    "story.deadlands.cinematics.header": "Cinematic Archive",
    "story.deadlands.cinematics.watch": "[Watch]",
    "story.deadlands.cinematics.watch.hover": "Watch it again, here, facing the way you face",
    "story.deadlands.cinematics.unseen": "Not yet seen",
    "story.deadlands.cinematics.count": "%s of %s seen.",
    "story.deadlands.cinematics.not_seen": "You haven't seen that one yet.",
    "cinematic.deadlands.opening": "The Opening",
    "cinematic.deadlands.boss_reveal": "Something Big",
    "cinematic.deadlands.research": "The Discovery",
    "cinematic.deadlands.horde": "The Horde",
    "cinematic.deadlands.reunion": "Day Ten: The Reunion",
    "cinematic.deadlands.family_house": "The Porch Light",
    "cinematic.deadlands.pinecrest": "Pinecrest",
    "cinematic.deadlands.cure": "The Cure",
    "cinematic.deadlands.extraction": "Extraction",
}


def build() -> None:
    D.merge_lang(TEXT)
    print(f"  round 8: {len(TEXT)} lines")


if __name__ == "__main__":
    build()
