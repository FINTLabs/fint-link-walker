package no.fint.linkwalker

import spock.lang.Specification

class PwfUtilsSpec extends Specification {

    def "Base url is pwf"() {
        expect:
        PwfUtils.isPwf(Constants.PWF_BASE_URL)
    }

    def "Base url is not pwf"() {
        expect:
        !PwfUtils.isPwf('http://localhost')
    }
}
