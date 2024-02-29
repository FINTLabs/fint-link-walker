package no.fint.linkwalker.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import no.fint.linkwalker.data.DiscoveredRelation
import spock.lang.Specification

class TestCaseSpec extends Specification {
    private ObjectMapper objectMapper
    private TestCase testCase

    void setup() {
        objectMapper = new ObjectMapper()
        testCase = new TestCase('org', new TestRequest('baseUrl', 'endpoint', 'orgId', 'client'))
        testCase.addRelation(new DiscoveredRelation(rel: 'self', links: [new URL('http://localhost')]))
    }

    def "Serialize all fields"() {
        when:
        def json = objectMapper.writerWithView(TestCaseViews.Details.class).writeValueAsString(testCase)

        then:
        // assertThat(json, hasJsonPath('$.id'))
        // assertThat(json, hasJsonPath('$.relations'))
        Object document = com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().parse(json)
        JsonPath.read(document, '$.id') != ""
        JsonPath.read(document, '$.relations') != ""
    }

    def "Serialize only result with id"() {
        when:
        def json = objectMapper.writerWithView(TestCaseViews.ResultsOverview.class).writeValueAsString(testCase)

        then:
        Object document = com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().parse(json)
        JsonPath.read(document, '$.id') != ""
    }

    def "Serialize only result without relations"() {
        when:
        def json = objectMapper.writerWithView(TestCaseViews.ResultsOverview.class).writeValueAsString(testCase)
        Object document = com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().parse(json)
        JsonPath.read(document, '$.relations')

        then:
        thrown(PathNotFoundException)
    }

    def "Filter relations running"() {
        given:
        testCase.start()

        when:
        def result = testCase.filterAndCopyRelations(Status.RUNNING)

        then:
        result.relations['self'].size() == 1
    }

    def "Filter relations failed"() {
        when:
        def result = testCase.filterAndCopyRelations(Status.FAILED)

        then:
        result.relations['self'].size() == 0
    }

}
