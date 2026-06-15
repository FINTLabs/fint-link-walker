package no.novari.linkwalker.config

import no.novari.linkwalker.report.ProblemType
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
class ProblemTypeConverter : Converter<String, ProblemType> {
    override fun convert(source: String): ProblemType =
        ProblemType.parseOrNull(source)
            ?: throw IllegalArgumentException("Unknown problemType: '$source'")
}
