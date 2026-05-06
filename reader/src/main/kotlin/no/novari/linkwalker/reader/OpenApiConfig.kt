package no.novari.linkwalker.reader

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info

@OpenAPIDefinition(
    info = Info(
        title = "FINT Link Walker API",
        version = "1.0.0",
        description = "Per-org link-integrity scan reports for the FINT data platform. " +
            "Surfaces aggregate counts, per-component / per-resource integrity percentages, " +
            "and paginated broken-link rows produced by the scheduled scanner.",
        contact = Contact(
            name = "@nozoz",
            url = "https://github.com/nozoz",
        ),
    ),
)
class OpenApiConfig
