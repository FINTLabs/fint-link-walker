package no.novari.linkwalker.index

import no.novari.linkwalker.config.IndexProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.nio.file.Files
import java.nio.file.Path

class AutoRelationsYamlParityTest {

    private data class Rule(
        val source: String,
        val relation: String,
        val target: String,
        val backRelation: String,
    )

    @Test
    fun `auto-relations yaml has exactly the rules documented in AUTORELATION_RULES md`() {
        val documented = documentedRules()
        val configured = configuredRules()

        assertEquals(emptySet<Rule>(), documented - configured.toSet(), "documented but missing from auto-relations.yaml")
        assertEquals(emptySet<Rule>(), configured.toSet() - documented, "in auto-relations.yaml but not documented")
        assertEquals(configured.size, configured.toSet().size, "duplicate rules in auto-relations.yaml")
    }

    private fun configuredRules(): List<Rule> {
        val sources = YamlPropertySourceLoader().load("auto-relations", ClassPathResource("auto-relations.yaml"))
        val binder = Binder(ConfigurationPropertySources.from(sources))
        return binder.bind("fint.link-walker.index", IndexProperties::class.java).get()
            .autoRelations
            .map { Rule(it.source, it.relation, it.target, it.backRelation) }
    }

    private fun documentedRules(): Set<Rule> {
        val heading = Regex("^## `([^`]+)`$")
        val row = Regex("^\\| `([^`]+)` \\| `([^`]+)` \\| `([^`]+)` \\| [^|]+ \\|$")
        var source: String? = null
        val rules = mutableSetOf<Rule>()
        Files.readAllLines(rulesDocument()).forEach { line ->
            heading.matchEntire(line)?.let {
                source = it.groupValues[1].dashed()
                return@forEach
            }
            val match = row.matchEntire(line) ?: return@forEach
            val (relation, target, backRelation) = match.destructured
            rules += Rule(checkNotNull(source) { "table row before any heading: $line" }, relation, target.dashed(), backRelation)
        }
        return rules
    }

    private fun String.dashed(): String = replace('/', '-')

    private fun rulesDocument(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("AUTORELATION_RULES.md") }
            .first { Files.exists(it) }
}
