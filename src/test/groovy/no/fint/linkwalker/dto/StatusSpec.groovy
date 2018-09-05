package no.fint.linkwalker.dto

import spock.lang.Specification

class StatusSpec extends Specification {

    def "Get ok status"() {
        when:
        def status = Status.get('ok')

        then:
        status == Status.OK
    }

    def "Return failed status if status string was not found"() {
        when:
        def status = Status.get('unknown status')

        then:
        status == Status.FAILED
    }
}
