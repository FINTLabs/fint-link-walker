package no.fint.linkwalker.data;

import lombok.Data;

import java.net.URL;
import java.util.Set;

@Data
public class DiscoveredRelation {
    private String rel;
    private Set<URL> links;
    private URL parentUrl;
}