package no.novari.linkwalker.index

data class PageExtraction(
    val records: List<MinimalRecord>,
    val totalItems: Long?,
    val nextHref: String? = null,
    val entryCount: Int = records.size,
    val malformedSelfCount: Int = 0,
)
