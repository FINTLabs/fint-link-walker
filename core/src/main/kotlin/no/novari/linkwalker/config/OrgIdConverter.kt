package no.novari.linkwalker.config

import no.novari.linkwalker.OrgId
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
@ConfigurationPropertiesBinding
class OrgIdConverter : Converter<String, OrgId> {
    override fun convert(source: String): OrgId = OrgId(source)
}
