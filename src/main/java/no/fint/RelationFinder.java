package no.fint;

import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private RelationFinder() {}

    public static Collection<Relation> findLinks(InputStream jsonStream) {
        Configuration conf = Configuration.builder()
                .options(Option.AS_PATH_LIST).build();

        String json = read(jsonStream);
        if (json == null || json.length() <= 0) {
            return Collections.emptyList();
        }

        DocumentContext pathDocument = JsonPath.using(conf).parse(json);
        DocumentContext valueDocument = JsonPath.parse(json);

        List<String> allLinkPaths = pathDocument.read("$.._links");

        return allLinkPaths.stream().flatMap(path -> {
            Map<String, List<Map<String, String>>> linkObject = valueDocument.read(path, new TypeRef<Map<String, List<Map<String, String>>>>(){});
            List<Relation> relations = linkObject.keySet().stream().map(rel -> arg(path, linkObject, rel)).collect(Collectors.toList());
            return relations.stream();
        }).collect(Collectors.toList());
    }

    private static Relation arg(String path, Map<String, List<Map<String, String>>> linkObject, String rel) {
        Relation relation = new Relation();
        relation.path = path;
        relation.rel = rel;
        relation.links = linkObject.get(rel).stream()
                .map(map -> map.get("href"))
                .map(RelationFinder::stringToURL)
                .collect(Collectors.toList());
        return relation;
    }

    private static URL stringToURL(String string) {
        try {
            return new URL(string);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);// TODO: our own exceptions? should probably be reported to users if there's something this wrong with the model.
        }
    }

    private static String read(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining());
    }

    public static class Relation {
        private String path;
        private String rel;
        private Collection<URL> links;

        public String getPath() {
            return path;
        }

        public String getRel() {
            return rel;
        }

        public Collection<URL> getLinks() {
            return links;
        }
    }
}
