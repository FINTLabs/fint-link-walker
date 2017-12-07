package no.fint;

import java.net.URL;
import java.util.Set;

/**
 * A Discovered relation is the collection of links of a particular rel, including the path in the document where it was found
 */
public class DiscoveredRelation {
    private String path;
    private String rel;
    private Set<URL> links;

    public String getPath() {
        return path;
    }

    public String getRel() {
        return rel;
    }

    public Set<URL> getLinks() {
        return links;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

    public void setLinks(Set<URL> links) {
        this.links = links;
    }
}