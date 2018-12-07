package no.fint.linkwalker;

import lombok.Data;

import java.net.URL;
import java.util.Set;

@Data
public class DiscoveredRelation {
    private String rel;
    private Set<URL> links;
    private URL parentUrl;
}