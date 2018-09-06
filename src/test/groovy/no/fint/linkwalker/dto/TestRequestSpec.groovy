package no.fint.linkwalker.dto

import no.fint.linkwalker.Constants
import spock.lang.Specification

class TestRequestSpec extends Specification {

    def "Get target with baseUrl as input"() {
        given:
        def request = new TestRequest('http://localhost', '/test', 'pwf.no', 'test')

        when:
        def target = request.getTarget()

        then:
        target == 'http://localhost/test'
    }

    def "Get target with default baseUrl"() {
        given:
        def request = new TestRequest(null, '/test', 'pwf.no', 'test')

        when:
        def target = request.getTarget()

        then:
        target == "${Constants.PWF_BASE_URL}/test"
    }
}
