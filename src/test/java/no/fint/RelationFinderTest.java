package no.fint;

import org.junit.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class RelationFinderTest {

    @Test
    public void person_json_contains_31_relations() {
        Collection<RelationFinder.Relation> relations = RelationFinder.findLinks(RelationFinderTest.class.getResourceAsStream("/person.json"));
        assertThat(relations).hasSize(31);
    }

    @Test
    public void empty_json_should_succeed_with_zero_releations() {
        Collection<RelationFinder.Relation> relations = RelationFinder.findLinks(RelationFinderTest.class.getResourceAsStream("/empty.json"));
        assertThat(relations).hasSize(0);
    }

}
