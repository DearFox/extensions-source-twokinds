package eu.kanade.tachiyomi.extension.ru.twokindsru

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class TwoKindsruFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(TwoKinds())
}
