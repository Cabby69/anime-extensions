package eu.kanade.tachiyomi.animeextension.all.pornhd3x

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PornHD3x : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "pornhd3x"
    override val baseUrl = "https://pornhd4k.net/"
    override val lang = "all"
    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    override fun popularAnimeSelector(): String =
        "div#main div#content div.mozaique.cust-nb-cols > div"

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/new/$page")

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(
                "$baseUrl${element.select("div.thumb-inside div.thumb a").attr("href")}",
            )
            title = element.select("div.thumb-under p.title").text()
            thumbnail_url =
                element.select("div.thumb-inside div.thumb a img").attr("data-src")
        }
    }

    override fun popularAnimeNextPageSelector(): String =
        "a.no-page.next-page"

    override fun episodeListParse(response: Response): List<SEpisode> =
        listOf(
            SEpisode.create().apply {
                name = "Video"
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = System.currentTimeMillis()
            },
        )

    override fun episodeListSelector() = throw Exception("not used")
    override fun episodeFromElement(element: Element) = throw Exception("not used")

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val sources = document
            .select("script:containsData(html5player.setVideoUrl)")
            .toString()

        val low = sources.substringAfter("VideoUrlLow('", "").substringBefore("')")
        val hls = sources.substringAfter("setVideoHLS('", "").substringBefore("')")
        val high = sources.substringAfter("VideoUrlHigh('", "").substringBefore("')")

        if (low.isBlank() && hls.isBlank() && high.isBlank()) {
            return emptyList()
        }

        return listOf(
            Video(low, "Low", low),
            Video(hls, "HLS", hls),
            Video(high, "High", high),
        )
    }

    override fun videoListSelector() = throw Exception("not used")
    override fun videoUrlParse(document: Document) = throw Exception("not used")
    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "HLS") ?: return this
        return sortedByDescending { it.quality == quality }
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val tag = filters.find { it is Tags } as Tags

        return when {
            query.isNotBlank() -> GET("$baseUrl/?k=$query&p=$page", headers)
            tag.state.isNotBlank() -> GET("$baseUrl/tags/${tag.state}/$page")
            else -> GET("$baseUrl/new/$page", headers)
        }
    }

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String =
        popularAnimeNextPageSelector()

    override fun searchAnimeSelector(): String =
        popularAnimeSelector()

    override fun animeDetailsParse(document: Document): SAnime =
        SAnime.create().apply {
            title = document.select("h2.page-title").text()
            description = ""
            genre =
                document.select("div.video-metadata ul li a span")
                    .joinToString { it.text() }
            status = SAnime.COMPLETED
        }

    override fun latestUpdatesNextPageSelector() = throw Exception("not used")
    override fun latestUpdatesFromElement(element: Element) = throw Exception("not used")
    override fun latestUpdatesRequest(page: Int) = throw Exception("not used")
    override fun latestUpdatesSelector() = throw Exception("not used")

    override fun getFilterList(): AnimeFilterList =
        AnimeFilterList(
            AnimeFilter.Header("Search by text does not affect the filter"),
            Tags("Tag"),
        )

    internal class Tags(name: String) : AnimeFilter.Text(name)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val pref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("High", "Low", "HLS")
            entryValues = arrayOf("High", "Low", "HLS")
            setDefaultValue("HLS")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
                true
            }
        }
        screen.addPreference(pref)
    }
}
