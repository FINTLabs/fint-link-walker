package no.fintlabs.linkwalker.request.model;

import java.util.List;
import java.util.Map;

public record Entry(Map<String, List<Link>> _links) {
}
