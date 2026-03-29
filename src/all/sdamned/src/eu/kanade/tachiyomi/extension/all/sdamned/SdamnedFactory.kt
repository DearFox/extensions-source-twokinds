package eu.kanade.tachiyomi.extension.all.sdamned

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class SdamnedFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Sdamned("en", "english"),
        Sdamned("ru", "russian"),
    )
}
