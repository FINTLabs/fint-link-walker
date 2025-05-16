package no.fintlabs.linkwalker.model

data class Entry(
    val selfLink: String,
    val ids: Map<String, Set<String>>
) {

    val idCount: Int
        get() = ids.values.sumOf { it.size }

}