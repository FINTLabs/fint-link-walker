package no.novari.linkwalker.index

data class PageExtraction(
    val records: List<MinimalRecord>,
    val totalItems: Long?,
)
