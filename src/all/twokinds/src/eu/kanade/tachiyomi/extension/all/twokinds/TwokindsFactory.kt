package eu.kanade.tachiyomi.extension.all.twokinds

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class TwokindsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        TwoKinds("en", "english"),
        TwoKinds("ru", "russian"),
    )
}
