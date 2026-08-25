package no.novari.linkwalker.report

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Closed set of broken-link classifications. The [wire] form is the public
 * contract for JSON and DB storage; the in-memory representation is a sealed
 * interface so callers can `when`-exhaust over it and the compiler enforces
 * coverage.
 */
sealed interface ProblemType {
    @get:JsonValue
    val wire: String

    data object MissingResource : ProblemType {
        override val wire: String = "missing-resource"
    }

    data object UnknownLink : ProblemType {
        override val wire: String = "unknown-link"
    }

    data object MissingBackLinkAdapter : ProblemType {
        override val wire: String = "missing-back-link-adapter"
    }

    data object MissingBackLinkAutorelation : ProblemType {
        override val wire: String = "missing-back-link-autorelation"
    }

    companion object {
        private val byWire: Map<String, ProblemType> = listOf(
            MissingResource, UnknownLink, MissingBackLinkAdapter, MissingBackLinkAutorelation,
        ).associateBy { it.wire }

        fun parseOrNull(wire: String): ProblemType? = byWire[wire]

        @JvmStatic
        @JsonCreator
        fun fromJson(wire: String): ProblemType =
            parseOrNull(wire) ?: throw IllegalArgumentException("Unknown problem type: '$wire'")
    }
}
