package no.fint;

import lombok.Data;

import java.net.URL;
import java.util.Set;

/**
 * A Discovered relation is the collection of links of a particular rel, including the path in the document where it was found
 */
@Data
public class DiscoveredRelation {
    private String path;
    private String rel;
    private Set<URL> links;
}