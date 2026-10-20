package me.ash.reader.qa

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mqa screen map (src/debug/assets/mqa-screens.json) must list every tagged screen and overlay of the
 * code, and nothing else: Navigation 3 keys, the literal pane/activity tags, and every overlay kind.
 * Paths are relative to the app module directory (the unit-test working directory).
 */
class ScreenMapTest {
    private val main = File("src/main/java/me/ash/reader")
    private val sources by lazy { main.walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList() }
    private val mapTags by lazy {
        Regex("\"tag\":\\s*\"([\\w.]+)\"").findAll(File("src/debug/assets/mqa-screens.json").readText())
            .map { it.groupValues[1] }.toList()
    }

    @Test
    fun everyDestinationIsInTheMap() {
        val keys = Regex("""data (?:object|class) (\w+)""")
            .findAll(File(main, "ui/page/nav3/key/NavKey.kt").readText()).map { it.groupValues[1] }.toSet()
        val literal = sources.flatMap { s ->
            (Regex("""TaggedScreen\("(\w+)"""").findAll(s) + Regex("""screenTag\("(\w+)"\)""").findAll(s))
                .map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals((keys + literal).toSortedSet(), mapTags.filter { '.' !in it }.toSortedSet())
    }

    @Test
    fun everyOverlayKindIsInTheMap() {
        val kinds = sources.flatMap { s -> Regex("""overlayTagName\("(\w+)"\)""").findAll(s).map { it.groupValues[1] }.toList() }.toSet() -
            "Dialog" // RYDialog's fallback for a call site with no overlayTag; every call site passes its own
        assertEquals(kinds.toSortedSet(), mapTags.filter { '.' in it }.map { it.substringAfter('.') }.toSortedSet())
    }

    @Test
    fun noTagIsListedTwice() {
        assertEquals(mapTags.distinct(), mapTags)
    }
}
