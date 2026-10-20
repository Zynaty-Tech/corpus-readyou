package me.ash.reader.qa

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import me.ash.reader.R
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.preference.SyncIntervalPreference
import me.ash.reader.infrastructure.preference.SyncOnStartPreference
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.getDefaultGroupId
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.ext.spacerDollar

/**
 * Debug-only QA seeds for mqa (skill qa-seeds). Each scenario first clears the data it owns, then writes it
 * fresh, so loading it twice gives the same state. No network: every feed URL uses the reserved `.invalid`
 * TLD (a sync fetches nothing), images are bundled in src/debug/assets/qa. Dates are fixed offsets from
 * now (hours ago), so the articles stay inside the default 30-day keep-archived window.
 *
 * Scenarios:
 * - `feeds-mixed`: local account "Read You" (current) with 3 groups (Default, Tech, News), 5 feeds
 *   (notifications on, full content on, open in browser on, with and without icons) and 14 articles
 *   (unread, read, starred, with and without images, long text); a second local account "QA Second"
 *   with one feed and 2 articles. Terms accepted (no Startup screen).
 * - `empty`: the current account with only the Default group, no feed; no second account.
 * - `first-launch`: only marks the terms as not accepted, so the next start shows the Startup screen.
 */
object QaSeeder {
    const val DEFAULT = "feeds-mixed"
    private const val SECOND = "QA Second"
    private const val ASSET = "file:///android_asset/qa/"
    /** Inline images of the article HTML: the WebView renderer serves this path from the app's assets. */
    private const val INLINE = "https://assets.qa.invalid/android_asset_font/qa/"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Daos {
        fun accountDao(): AccountDao
        fun groupDao(): GroupDao
        fun feedDao(): FeedDao
        fun articleDao(): ArticleDao
    }

    suspend fun seed(context: Context, scenario: String?): Result<String> = runCatching {
        val name = scenario ?: DEFAULT
        val d = EntryPointAccessors.fromApplication(context, Daos::class.java)
        when (name) {
            "feeds-mixed" -> {
                val a = primaryAccount(context, d)
                clearAccount(d, a)
                writeMixed(context, d, a)
                val b = d.accountDao().queryAll().firstOrNull { it.name == SECOND && it.id != a }
                    ?.id ?: d.accountDao().insert(Account(name = SECOND, type = AccountType.Local)).toInt()
                deleteOtherAccounts(d, keep = setOf(a, b))
                clearAccount(d, b)
                writeSecond(context, d, b)
                finish(context, d, a)
            }
            "empty" -> {
                val a = primaryAccount(context, d)
                clearAccount(d, a)
                deleteOtherAccounts(d, keep = setOf(a))
                d.groupDao().insert(Group(a.getDefaultGroupId(), context.getString(R.string.defaults), a))
                finish(context, d, a)
            }
            "first-launch" -> context.dataStore.put(DataStoreKey.isFirstLaunch, true)
            else -> error("unknown scenario $name")
        }
        "seeded $name"
    }

    /** The app creates its default account on the first start, on a background thread; wait for it. */
    private suspend fun primaryAccount(context: Context, d: Daos): Int {
        val first = withTimeoutOrNull(15_000) {
            while (d.accountDao().queryAll().isEmpty()) delay(200)
            d.accountDao().queryAll().first()
        }
        if (first == null) {
            val id = d.accountDao().insert(Account(name = context.getString(R.string.read_you), type = AccountType.Local)).toInt()
            d.groupDao().insert(Group(id.getDefaultGroupId(), context.getString(R.string.defaults), id))
            return id
        }
        val id = first.id!!
        // initWithDefaultAccount inserts its first feed right after the account: let it finish, then replace it.
        withTimeoutOrNull(3_000) { while (d.feedDao().queryAll(id).isEmpty()) delay(200) }
        delay(500)
        return id
    }

    private suspend fun clearAccount(d: Daos, id: Int) {
        d.articleDao().deleteByAccountId(id)
        d.feedDao().deleteByAccountId(id)
        d.groupDao().deleteByAccountId(id)
    }

    private suspend fun deleteOtherAccounts(d: Daos, keep: Set<Int>) {
        d.accountDao().queryAll().filter { it.id !in keep }.forEach {
            clearAccount(d, it.id!!)
            d.accountDao().delete(it)
        }
    }

    private suspend fun finish(context: Context, d: Daos, a: Int) {
        d.accountDao().queryById(a)?.let {
            d.accountDao().update(it.copy(syncInterval = SyncIntervalPreference.Manually, syncOnStart = SyncOnStartPreference.Off))
        }
        context.dataStore.put(DataStoreKey.currentAccountId, a)
        context.dataStore.put(DataStoreKey.currentAccountType, AccountType.Local.id)
        context.dataStore.put(DataStoreKey.isFirstLaunch, false)
    }

    private class A(val n: Int, val feed: String, val title: String, val hoursAgo: Int, val unread: Boolean,
                    val starred: Boolean, val img: String?, val html: String)

    private suspend fun writeMixed(context: Context, d: Daos, a: Int) {
        val g0 = a.getDefaultGroupId()
        val g1 = a spacerDollar "qa_group_tech"
        val g2 = a spacerDollar "qa_group_news"
        d.groupDao().insert(
            Group(g0, context.getString(R.string.defaults), a),
            Group(g1, "Tech", a),
            Group(g2, "News", a),
        )
        fun f(key: String) = a spacerDollar "qa_feed_$key"
        d.feedDao().insert(
            Feed(f("notes"), "QA Daily Notes", null, "https://notes.qa.invalid/feed.xml", g0, a, isNotification = true),
            Feed(f("tech"), "QA Tech Daily", ASSET + "icon_tech.png", "https://tech.qa.invalid/rss", g1, a),
            Feed(f("photo"), "QA Photo Blog", ASSET + "icon_photo.png", "https://photos.qa.invalid/atom.xml", g1, a),
            Feed(f("news"), "QA World News", ASSET + "icon_news.png", "https://news.qa.invalid/rss", g2, a, isFullContent = true),
            Feed(f("links"), "QA Link Digest", null, "https://links.qa.invalid/feed", g2, a, isBrowser = true),
        )
        val p = { t: String -> "<p>$t</p>" }
        val img = { file: String, alt: String -> "<p><img src=\"$INLINE$file\" alt=\"$alt\" /></p>" }
        val articles = listOf(
            A(1, "notes", "Welcome to the QA notes", 1, true, false, null,
                p("This local feed holds plain text notes.") + p("Swipe an article, long press it, or open it to read.")),
            A(2, "notes", "Shopping list for the weekend", 26, false, false, null,
                p("Things to buy:") + "<ul><li>Bread</li><li>Apples</li><li>Coffee</li></ul>"),
            A(3, "notes", "A note with a long description", 50, true, true, null,
                (1..12).joinToString("") { p("Paragraph $it of a long note. It is long so the reader scrolls and the toolbars hide.") }),
            A(4, "tech", "Kotlin release notes, explained", 2, true, false, "city_night.png",
                "<h2>What is new</h2>" + p("A short summary with <a href=\"https://tech.qa.invalid/kotlin\">a link</a>.") +
                    img("city_night.png", "A city at night") + "<pre><code>fun main() = println(\"Hello\")</code></pre>"),
            A(5, "tech", "How a phone battery works", 30, false, true, "garden.png",
                p("Batteries store energy in chemical form.") + img("garden.png", "A green garden")),
            A(6, "tech", "Ten tips for faster builds", 75, true, false, null,
                "<ol>" + (1..10).joinToString("") { "<li>Tip number $it</li>" } + "</ol>"),
            A(7, "photo", "Photo walk: autumn colors", 3, true, false, "photo_walk.png",
                p("A walk with a camera.") + img("photo_walk.png", "Autumn colors") + p("And one more picture.") +
                    img("garden.png", "A green garden") + "<blockquote>Tap a picture to open it full screen.</blockquote>"),
            A(8, "photo", "City lights at night", 28, false, false, "city_night.png",
                img("city_night.png", "A city at night") + p("Long exposure at dusk.")),
            A(9, "photo", "A quiet garden", 100, true, true, "garden.png",
                img("garden.png", "A green garden") + p("Morning light in the garden.")),
            A(10, "news", "Morning briefing", 4, true, false, null,
                p("The top three stories of the day, in short.")),
            A(11, "news", "Weather: rain expected", 52, false, false, null,
                p("Rain is expected for the next two days.")),
            A(12, "news", "Election results explained", 120, true, true, "photo_walk.png",
                p("What the results mean.") + img("photo_walk.png", "Autumn colors")),
            A(13, "links", "Five links worth reading", 5, true, false, null,
                p("Opens in the browser: this feed has Open in browser on.")),
            A(14, "links", "Weekly digest", 80, false, false, null,
                p("Last week's links.")),
        )
        val now = System.currentTimeMillis()
        d.articleDao().insertList(articles.map { x ->
            val text = x.html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            Article(
                id = a spacerDollar "qa_article_${x.n}",
                date = Date(now - x.hoursAgo * 3_600_000L),
                title = x.title,
                author = if (x.n % 3 == 0) "QA Author" else null,
                rawDescription = x.html,
                shortDescription = text.take(120),
                img = x.img?.let { ASSET + it },
                link = "https://${x.feed}.qa.invalid/posts/${x.n}",
                feedId = f(x.feed),
                accountId = a,
                isUnread = x.unread,
                isStarred = x.starred,
                updateAt = Date(now - x.hoursAgo * 3_600_000L),
            )
        })
    }

    private suspend fun writeSecond(context: Context, d: Daos, b: Int) {
        val g = b.getDefaultGroupId()
        d.groupDao().insert(Group(g, context.getString(R.string.defaults), b))
        val feed = b spacerDollar "qa_feed_second"
        d.feedDao().insert(Feed(feed, "QA Second Feed", null, "https://second.qa.invalid/feed.xml", g, b))
        val now = System.currentTimeMillis()
        d.articleDao().insertList((1..2).map { n ->
            Article(
                id = b spacerDollar "qa_article_$n",
                date = Date(now - n * 7_200_000L),
                title = "Second account article $n",
                rawDescription = "<p>An article of the second local account.</p>",
                shortDescription = "An article of the second local account.",
                link = "https://second.qa.invalid/posts/$n",
                feedId = feed,
                accountId = b,
                isUnread = n == 1,
            )
        })
    }
}
