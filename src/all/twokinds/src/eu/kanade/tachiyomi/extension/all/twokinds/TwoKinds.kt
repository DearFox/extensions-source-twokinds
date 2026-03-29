package eu.kanade.tachiyomi.extension.all.twokinds

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class TwoKinds(
    override val lang: String,
    private val twokindsLang: String,
) : HttpSource(),
    ConfigurableSource {

    // Конфигурация для каждого языка
    private val siteConfig: SiteConfig = when (twokindsLang) {
        "english" -> SiteConfig(
            name = "TwoKinds",
            baseUrl = "https://twokinds.keenspot.com",
            id = 3133607736276627986L,
            thumbnailUrl = "https://cdn.twokinds.keenspot.com/comics/20031214.jpg",
            archiveSelector = "section.chapter",
            chapterLinksSelector = ".chapter-links a",
            imageSelector = "#content article img",
        )
        "russian" -> SiteConfig(
            name = "TwoKinds",
            baseUrl = "https://twokinds.ru",
            id = 3133707736276627986L,
            thumbnailUrl = "https://twokinds.ru/comic/6/page",
            archiveSelector = "section.Chapter",
            chapterLinksSelector = ".ChapterLinks a",
            imageSelector = "#comic header img",
        )
        else -> SiteConfig(
            name = "TwoKinds",
            baseUrl = "https://twokinds.keenspot.com",
            id = 5133607736276627986L,
            thumbnailUrl = "https://cdn.twokinds.keenspot.com/comics/20031214.jpg",
            archiveSelector = "section.chapter",
            chapterLinksSelector = ".chapter-links a",
            imageSelector = "#content article img",
        )
    }

    data class SiteConfig(
        val name: String,
        val baseUrl: String,
        val id: Long,
        val thumbnailUrl: String,
        val archiveSelector: String,
        val chapterLinksSelector: String,
        val imageSelector: String,
    )

    override val name: String get() = siteConfig.name
    override val baseUrl: String get() = siteConfig.baseUrl
    override val id: Long get() = siteConfig.id
    override val supportsLatest: Boolean = false

    // Кэш данных глав
    private var chaptersCache: List<ChapterData>? = null

    data class ChapterData(
        val id: String,
        val name: String,
        val pages: List<PageRef>,
    )

    data class PageRef(
        val urlPart: String,
        val pageName: String,
    )

    private val preferences: SharedPreferences by getPreferencesLazy()

    // ============================================================
    // Manga
    // ============================================================
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(mangaDetails), false))

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(mangaDetails)

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    private val mangaDetails: SManga
        get() = SManga.create().apply {
            title = "TwoKinds"
            thumbnail_url = siteConfig.thumbnailUrl
            artist = "Tom Fischbach"
            author = "Tom Fischbach"
            status = SManga.UNKNOWN
            url = "0"
        }

    // ============================================================
    // Chapters
    // ============================================================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .map { response -> chapterListParse(response, manga) }

    override fun chapterListRequest(manga: SManga): Request = GET("${siteConfig.baseUrl}/archive/", headers)

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val chapters = fetchChaptersData(response)
        return chapters.map { chapter ->
            SChapter.create().apply {
                url = chapter.id
                name = chapter.name
            }
        }.reversed()
    }

    // ============================================================
    // Pages
    // ============================================================
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapters = fetchChaptersData()
        val chapterData = chapters.find { it.id == chapter.url }
            ?: return Observable.error(Exception("Глава не найдена: ${chapter.url}"))

        val pages = chapterData.pages.mapIndexed { idx, pageRef ->
            Page(idx, "${siteConfig.baseUrl}/comic/${pageRef.urlPart}/")
        }
        return Observable.just(pages)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()
        val src = document.select(siteConfig.imageSelector).first()?.attr("src") ?: ""
        return if (src.startsWith("http://") || src.startsWith("https://")) {
            src
        } else {
            "${siteConfig.baseUrl}$src"
        }
    }

    // ============================================================
    // Search (не поддерживается)
    // ============================================================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    // ============================================================
    // ConfigurableSource
    // ============================================================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Добавьте здесь настройки, если потребуются
        // Пример:
        // SwitchPreferenceCompat(screen.context).apply {
        //     key = "some_preference"
        //     title = "Some setting"
        //     setDefaultValue(false)
        // }.also(screen::addPreference)
    }

    // ============================================================
    // Вспомогательные методы
    // ============================================================
    private fun fetchChaptersData(response: Response? = null): List<ChapterData> {
        chaptersCache?.let { return it }

        val resp = response ?: client.newCall(GET("${siteConfig.baseUrl}/archive/", headers)).execute()
        val document = resp.asJsoup()

        val chapters = document.select(siteConfig.archiveSelector).mapNotNull { section ->
            val id = section.attr("data-ch-id")
            if (id.isEmpty()) return@mapNotNull null

            val name = section.select("h2").first()?.text() ?: "Без названия"
            val pages = section.select(siteConfig.chapterLinksSelector).mapNotNull { a ->
                val href = a.attr("href")
                val urlPart = href.split("/").getOrNull(2) ?: return@mapNotNull null
                val pageName = a.select("span").first()?.text() ?: urlPart
                PageRef(urlPart, pageName)
            }
            ChapterData(id, name, pages)
        }

        chaptersCache = chapters
        return chapters
    }

    companion object {
        // Для регистрации источников в приложении
        fun createEnglish() = TwoKinds("en", "english")
        fun createRussian() = TwoKinds("ru", "russian")
        // fun createAll() = TwoKinds("all", "all")
    }
}
