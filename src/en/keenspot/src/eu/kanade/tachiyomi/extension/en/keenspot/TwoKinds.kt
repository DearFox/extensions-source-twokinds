package eu.kanade.tachiyomi.extension.en.keenspot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class TwoKinds : HttpSource() {

    override val name = "Keenspot TwoKinds"
    override val baseUrl = "https://twokinds.keenspot.com"
    override val lang = "en"
    override val supportsLatest: Boolean = false
    override val id: Long = 3133607736276627986

    // Кэш данных глав (список страниц по главам)
    private var chaptersCache: List<ChapterData>? = null

    // Структура для хранения информации о главе
    data class ChapterData(
        // data-ch-id
        val id: String,
        // текст из h2
        val name: String,
        // список страниц в главе
        val pages: List<PageRef>,
    )

    data class PageRef(
        // идентификатор страницы, например "6"
        val urlPart: String,
        // номер страницы из span
        val pageName: String,
    )

    // ------------------------------------------------------------
    // Manga (единственный комикс)
    // ------------------------------------------------------------
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
            thumbnail_url = "https://cdn.twokinds.keenspot.com/comics/20031214.jpg"
            artist = "Tom Fischbach"
            author = "Tom Fischbach"
            status = SManga.UNKNOWN
            // не используется
            url = "0"
        }

    // ------------------------------------------------------------
    // Chapters
    // ------------------------------------------------------------
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .map { response -> chapterListParse(response, manga) }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/archive/", headers)

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val chapters = fetchChaptersData(response)
        return chapters.map { chapter ->
            SChapter.create().apply {
                url = chapter.id
                name = chapter.name
            }
        }.reversed() // последние главы сверху
    }

    // ------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapters = fetchChaptersData() // используем кэш или загружаем
        val chapterData = chapters.find { it.id == chapter.url }
            ?: return Observable.error(Exception("Глава не найдена: ${chapter.url}"))

        val pages = chapterData.pages.mapIndexed { idx, pageRef ->
            Page(idx, "$baseUrl/comic/${pageRef.urlPart}/")
        }
        return Observable.just(pages)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()
        val src = document.select("#content article img").first()!!.attr("src")
        return if (src.startsWith("http://") || src.startsWith("https://")) src else "$baseUrl$src"
    }

    // ------------------------------------------------------------
    // Search (не поддерживается)
    // ------------------------------------------------------------
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    // ------------------------------------------------------------
    // Вспомогательные методы для работы с архивом
    // ------------------------------------------------------------
    private fun fetchChaptersData(response: Response? = null): List<ChapterData> {
        chaptersCache?.let { return it }

        val resp = response ?: client.newCall(GET("$baseUrl/archive/", headers)).execute()
        val document = resp.asJsoup()

        val chapters = document.select("section.chapter").mapNotNull { section ->
            val id = section.attr("data-ch-id")
            if (id.isEmpty()) return@mapNotNull null

            val name = section.select("h2").first()?.text() ?: "Без названия"
            val pages = section.select(".chapter-links a").mapNotNull { a ->
                val href = a.attr("href")
                // ожидаем формат /comic/число/ или /comic/что-то/
                val urlPart = href.split("/").getOrNull(2) ?: return@mapNotNull null
                val pageName = a.select("span").first()?.text() ?: urlPart
                PageRef(urlPart, pageName)
            }
            ChapterData(id, name, pages)
        }

        chaptersCache = chapters
        return chapters
    }
}
