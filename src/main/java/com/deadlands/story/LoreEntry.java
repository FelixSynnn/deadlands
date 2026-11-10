package com.deadlands.story;

import java.util.List;

/**
 * One lore entry. Its title and text are {@code lore.deadlands.<id>.title} and {@code .body}.
 *
 * @param places where it turns up (see {@code tools/assetgen/lore_data.py})
 * @param series the multi-part story it belongs to, or empty
 * @param part its place in the series, 0 if none
 * @param form what it looks like as an item: document, journal, newspaper, tape or photo
 */
public record LoreEntry(String id, LoreCategory category, List<String> places, String series, int part, String form) {
}
