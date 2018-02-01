package no.fint;

import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public final class RelationFinder {

    static {
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();
            private final MappingProvider mappingProvider = new JacksonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }

    public static Collection<DiscoveredRelation> findLinks(String json) {
        Configuration conf = Configuration.builder()
                .options(Option.AS_PATH_LIST).build();

        if (json == null || json.length() <= 0) {
            return Collections.emptyList();
        }

        DocumentContext pathDocument = JsonPath.using(conf).parse(json);
        DocumentContext valueDocument = JsonPath.parse(json);

        List<String> allLinkPaths = pathDocument.read("$.._links");

        return allLinkPaths.stream().flatMap(path -> {
            Map<String, List<Map<String, String>>> linkObject = valueDocument.read(path, new TypeRef<Map<String, List<Map<String, String>>>>() {
            });
            List<DiscoveredRelation> relations = linkObject.keySet().stream().map(rel -> createRelation(path, linkObject, rel)).collect(Collectors.toList());
            return relations.stream();
        }).collect(Collectors.toList());
    }

    private static DiscoveredRelation createRelation(String path, Map<String, List<Map<String, String>>> linkObject, String rel) {
        DiscoveredRelation relation = new DiscoveredRelation();
        relation.setPath(path);
        relation.setRel(rel);
        relation.setLinks(linkObject.get(rel).stream()
                .map(map -> map.get("href"))
                .map(RelationFinder::stringToURL)
                .collect(Collectors.toSet()));
        return relation;
    }

    private static URL stringToURL(String string) {
        try {
            return new URL(string);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);// TODO: our own exceptions? should probably be reported to users if there's something this wrong with the model.
        }
    }

}
