package eu.kanade.tachiyomi.extension.all.sdamned

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

class Sdamned(
    override val lang: String,
    private val sdamnedLang: String,
) : HttpSource(),
    ConfigurableSource {

    // Конфигурация для каждого языка
    private val siteConfig: SiteConfig = when (sdamnedLang) {
        "english" -> SiteConfig(
            name = "Slightly Damned",
            baseUrl = "https://www.sdamned.com",
            id = 3233607736276627986L,
            thumbnailUrl = "https://www.sdamned.com/comicsthumbs/1744589218-Book%201%20cover%20(2nd%20edition).png",
            archiveUrl = "https://www.sdamned.com/comic/archive/",
            selectPrefix = "comic",
        )
        "russian" -> SiteConfig(
            name = "Slightly Damned",
            baseUrl = "https://www.sdamned.com",
            id = 3233707736276627986L,
            thumbnailUrl = "https://www.sdamned.com/comicsthumbs/1744589218-Book%201%20cover%20(2nd%20edition).png",
            archiveUrl = "https://www.sdamned.com/russian/archive/",
            selectPrefix = "russian",
        )
        else -> SiteConfig(
            name = "Slightly Damned",
            baseUrl = "https://www.sdamned.com",
            id = 5233607736276627986L,
            thumbnailUrl = "https://www.sdamned.com/comicsthumbs/1744589218-Book%201%20cover%20(2nd%20edition).png",
            archiveUrl = "https://www.sdamned.com/comic/archive/",
            selectPrefix = "comic",
        )
    }

    data class SiteConfig(
        val name: String,
        val baseUrl: String,
        val id: Long,
        val thumbnailUrl: String,
        val archiveUrl: String,
        val selectPrefix: String,
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
        val pages: MutableList<PageRef>,
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
            title = "Slightly Damned"
            thumbnail_url = siteConfig.thumbnailUrl
            artist = "Chu"
            author = "Chu"
            status = SManga.UNKNOWN
            url = "0"
        }

    // ============================================================
    // Chapters
    // ============================================================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .map { response -> chapterListParse(response, manga) }

    override fun chapterListRequest(manga: SManga): Request = GET(siteConfig.archiveUrl, headers)

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
            Page(idx, "${siteConfig.baseUrl}/${pageRef.urlPart}/")
        }
        return Observable.just(pages)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()
        val src = document.select("img#cc-comic").first()?.attr("src") ?: ""
        return if (src.startsWith("http://") || src.startsWith("https://")) {
            src
        } else {
            "${siteConfig.baseUrl}/$src"
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
    }

    // ============================================================
    // Вспомогательные методы
    // ============================================================
    private fun fetchChaptersData(response: Response? = null): List<ChapterData> {
        chaptersCache?.let { return it }

        val resp = response ?: client.newCall(GET(siteConfig.archiveUrl, headers)).execute()
        val document = resp.asJsoup()

        val chapters = mutableListOf<ChapterData>()
        var currentChapter: ChapterData? = null

        // Находим элемент select с именем "comic"
        val selectOptions = document.select("select[name=comic] option")

        for (option in selectOptions) {
            val value = option.attr("value")
            val text = option.text()

            // Пропускаем пустой опцион
            if (value.isEmpty()) continue

            // Извлекаем URL часть (всё после префикса)
            // val urlPart = value.removePrefix("${siteConfig.selectPrefix}/")
            val urlPart = value

            // Проверяем, является ли это названием главы или номером страницы
            // Название главы содержит текст после тире, который не является числом
            val parts = text.split(" - ", limit = 2)
            if (parts.size < 2) continue

            val contentAfterDash = parts[1].trim()

            // Если содержимое после тире - это просто число, это страница текущей главы
            if (contentAfterDash.toIntOrNull() != null) {
                // Это номер страницы, добавляем в текущую главу
                currentChapter?.let { chapter ->
                    chapter.pages.add(PageRef(urlPart, contentAfterDash))
                }
            } else {
                // Это новая глава
                currentChapter?.let { chapters.add(it) }
                currentChapter = ChapterData(
                    id = urlPart,
                    name = contentAfterDash,
                    pages = mutableListOf(PageRef(urlPart, "1")),
                )
            }
        }

        // Добавляем последнюю главу
        currentChapter?.let { chapters.add(it) }

        chaptersCache = chapters
        return chapters
    }

    companion object {
        // Для регистрации источников в приложении
        fun createEnglish() = Sdamned("en", "english")
        fun createRussian() = Sdamned("ru", "russian")
    }
}
