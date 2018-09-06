package no.fint.linkwalker

import no.fint.linkwalker.dto.TestRequest
import no.fint.linkwalker.exceptions.NoSuchTestCaseException
import spock.lang.Specification

class TestCaseRepositorySpec extends Specification {
    def testCaseRepository = new TestCaseRepository()

    def "Build new test case and get all cases"() {
        when:
        def testCase = testCaseRepository.buildNewCase('pwf.no', new TestRequest('http://localhost', '/test', 'pwf.no', 'test'))
        def testCases = testCaseRepository.allTestCases('pwf.no')

        then:
        testCase == testCases[0]
    }

    def "Clear test cases for organization"() {
        given:
        testCaseRepository.buildNewCase('pwf.no', new TestRequest('http://localhost', '/test', 'pwf.no', 'test'))

        when:
        testCaseRepository.clearTests('pwf.no')
        def testCases = testCaseRepository.allTestCases('pwf.no')

        then:
        testCases.size() == 0
    }

    def "Throw exception when no test case is found for id"() {
        when:
        testCaseRepository.getCaseForId('pwf.no', UUID.randomUUID())

        then:
        thrown NoSuchTestCaseException
    }

    def "Get test case for id"() {
        given:
        def testCase = testCaseRepository.buildNewCase('pwf.no', new TestRequest('http://localhost', '/test', 'pwf.no', 'test'))

        when:
        def result = testCaseRepository.getCaseForId('pwf.no', testCase.getId())

        then:
        result == testCase
    }
}
