package no.fint.linkwalker

data class TestRequest(val baseUrl: String,
                       val path: String,
                       val orgId: String) {
}