package com.deadlands.npc;

import net.minecraft.util.RandomSource;

/** Names for survivors: a first name and a surname, about 1,500 combinations. */
public final class NpcNames {
	private static final String[] FIRST = {"Ada", "Ben", "Cora", "Dale", "Edith", "Frank", "Gwen", "Hank", "Iris", "Joel", "Kate", "Leon", "Mara", "Nate",
		"Opal", "Pete", "Quinn", "Rosa", "Sam", "Tess", "Uma", "Vern", "Wade", "Xena", "Yara", "Zeke", "Abel", "Beth", "Cal", "Dora", "Eli", "Fay", "Gus",
		"Hope", "Ivan", "June", "Kurt", "Lena", "Milo", "Nora"};
	private static final String[] LAST = {"Abbott", "Barlow", "Carver", "Dunn", "Ellery", "Foster", "Graves", "Hale", "Ingram", "Jarvis", "Keene", "Lowell",
		"Mercer", "Nash", "Oakley", "Pruitt", "Quill", "Reyes", "Sutter", "Thorne", "Underhill", "Vance", "Whitlock", "Yates", "Zeller", "Ashby", "Boone",
		"Crane", "Drake", "Easton", "Finch", "Gallo", "Hart", "Ives", "Joyce", "Kemp", "Lang", "Marsh"};

	private NpcNames() {
	}

	public static String pick(RandomSource random) {
		return FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
	}
}
