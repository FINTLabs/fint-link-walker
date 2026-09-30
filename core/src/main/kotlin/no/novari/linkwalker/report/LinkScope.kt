package no.novari.linkwalker.report

import com.fasterxml.jackson.annotation.JsonValue

/**
 * Where a link points, seen from the resource it comes from.
 *
 * [WithinDomain] and [CrossDomain] links are checked and count toward integrity. [NotCovered]
 * links point to a resource the scan did not fetch, so nobody knows whether they are right;
 * they are counted but never reported as errors.
 */
enum class LinkScope(@get:JsonValue val wire: String) {
    WithinDomain("within_domain"),
    CrossDomain("cross_domain"),
    NotCovered("not_covered"),
}
