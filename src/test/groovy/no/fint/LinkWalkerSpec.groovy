package no.fint

import spock.lang.Specification

class LinkWalkerSpec extends Specification {

    public static final String playsniceURL = "https://example.com/should/return/200OK"
    public static final String _404sURL = "https://example.com/should/return/404NotFound"
    public static final String _500sURL = "https://example.com/should/return/500InternalError"
    public static final String foundURL = "https://example.com/should/return/302Found"
    public static final String innerSelfURL = "https://example.com/200OK/andpossiblyADocument"
    public static final String selfURL = "https://example.com/should/return/200OK/and/this/document"

    HttpClient httpClient = buildReplacementHttpClient()
    TestCaseRepository caseRepository = new TestCaseRepository()
    TestRunner testRunner = new TestRunner(
            httpClient: httpClient
    )
    TestScheduler testScheduler = new TestScheduler(
            runner: testRunner,
            repository: caseRepository
    )
    TestController testController = new TestController(
            testScheduler: testScheduler,
            repository: caseRepository
    )

    /**
     * Creates an ersatz http-client that succeeds or fails according to plan
     */
    HttpClient buildReplacementHttpClient() {
        HttpClient.Response playsnice = new HttpClient.Response(200, "")
        HttpClient.Response _404s = new HttpClient.Response(404, "")
        HttpClient.Response _500s = new HttpClient.Response(500, "")
        HttpClient.Response found = new HttpClient.Response(302, "")
        HttpClient.Response innerSelf = new HttpClient.Response(200, "")
        HttpClient.Response self = new HttpClient.Response(200, HttpClient.getResourceAsStream("/exercise.json").text)

        new HttpClient() {
            @Override
            HttpClient.Response get(URL url) {
                switch (url.toString()) {
                    case playsniceURL: return playsnice
                    case _404sURL: return _404s
                    case _500sURL: return _500s
                    case foundURL: return found
                    case innerSelfURL: return innerSelf
                    case selfURL: return self
                    default: throw new RuntimeException(url.toString() + " is not a known url for this test")
                }
            }
        }
    }

    def "The link walker should test the response codes of the relations defined in the document it is asked to test"() {
        given:
        def initialTest = testController.startTest("should/return/200OK/and/this/document", new URL("https://example.com"))
        testScheduler.runATest()

        when:
        def completedTest = testController.getTest(initialTest.id)

        then:
        completedTest.status == Status.OK
        completedTest.relations.each { relationName, relation ->
            switch (relationName) {
                case "playsnice":
                    relation.each { rel -> rel.status == Status.OK }
                    break
                case "404s":
                    relation.each { rel -> rel.status == Status.FAILED }
                    break
                case "500s":
                    relation.each { rel -> rel.status == Status.FAILED }
                    break
                case "found":
                    relation.each { rel -> rel.status == Status.FAILED } // This should probably actually be OK, and the HttpClient should follow redirects.
                    break
                case "self":
                    relation.each { rel -> rel.status == Status.OK }
                    break
                default: throw new RuntimeException(relationName + " is not a known releation for this test")
            }
        }
    }
}
