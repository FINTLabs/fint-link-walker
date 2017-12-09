package no.fint

import spock.lang.Specification

class RelationFinderSpec extends Specification {

    def "person.json contains 31 relations"() {
        when:
        def relations = RelationFinder.findLinks(RelationFinder.getResourceAsStream("/person.json").readLines().join(""))

        then:
        relations.size() == 31
    }

    def "empty.json should succeed with zero relations"() {
        when:
        def relations = RelationFinder.findLinks(RelationFinder.getResourceAsStream("/empty.json").readLines().join(""))

        then:
        relations.size() == 0
    }
}
