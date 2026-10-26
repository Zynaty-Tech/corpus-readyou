package me.ash.reader.ui.qa

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation3.runtime.NavEntry
import me.ash.reader.BuildConfig

/**
 * Screen tags and control ids for external QA tools (mqa, UiAutomator). Debug builds only:
 * [BuildConfig.EXPOSE_TEST_TAGS] is false in defaultConfig and true in the debug build type, so the
 * release build publishes no tag. Screen names are code names (nav key class names or literals), never
 * visible text.
 */

/** The name of the screen (or overlay) that draws the current composition; null outside a screen. */
val LocalScreenName = staticCompositionLocalOf<String?> { null }

/** The prefix of control ids inside an overlay; null means the screen name. */
val LocalControlPrefix = staticCompositionLocalOf<String?> { null }

/** Publishes the test tags of this composition root as resource ids (debug only). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.exposeTestTags(): Modifier =
    if (BuildConfig.EXPOSE_TEST_TAGS) this.semantics { testTagsAsResourceId = true } else this

/** Tags the root of a screen, dialog or sheet: `screen:<name>` (debug only). */
fun Modifier.screenTag(name: String): Modifier =
    if (BuildConfig.EXPOSE_TEST_TAGS) this.testTag("screen:$name") else this

/** The tag of an overlay opened by the current screen: `<Host>.<kind>`, or `<kind>` with no host. */
@Composable
fun overlayTagName(kind: String): String = LocalScreenName.current?.let { "$it.$kind" } ?: kind

/** A stable control id: `<Screen or overlay>.<name>`, e.g. `Settings.Tap.Accounts`. */
@Composable
fun Modifier.controlTag(name: String): Modifier {
    val prefix = LocalControlPrefix.current ?: LocalScreenName.current ?: "App"
    return this.testTag("$prefix.$name")
}

/** Root of an overlay window: exposes tags, tags the overlay, and names its controls after it. */
fun Modifier.overlayRoot(tag: String): Modifier = this.exposeTestTags().screenTag(tag)

@Composable
fun ControlPrefix(prefix: String?, content: @Composable () -> Unit) {
    if (prefix == null) content() else CompositionLocalProvider(LocalControlPrefix provides prefix, content = content)
}

/** The screen name of a Navigation 3 key: the key's class name, never `key.toString()`. */
fun screenNameOf(key: Any): String =
    key::class.simpleName
        ?: key.toString().substringBefore(':').substringBefore('(').substringBefore('/').substringBefore('?').trim()

/** Wraps a nav entry so its content carries `screen:<name>` and provides [LocalScreenName]. */
fun <T : Any> screenTagged(key: T, entry: NavEntry<T>): NavEntry<T> =
    NavEntry(
        navEntry = entry,
        content = { k: T ->
            val name = screenNameOf(k)
            CompositionLocalProvider(LocalScreenName provides name) {
                Box(Modifier.screenTag(name)) { entry.Content() }
            }
        },
    )

/** Tags a pane or an activity root that is a screen of its own, and provides its name. */
@Composable
fun TaggedScreen(name: String, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenName provides name) {
        Box(if (enabled) modifier.screenTag(name) else modifier) { content() }
    }
}

/** The key of a radio or menu option: the option object's class name (an enum-like preference object). */
fun optionKey(value: Any): String = value::class.simpleName ?: "Item"
