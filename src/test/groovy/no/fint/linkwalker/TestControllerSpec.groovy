package no.fint.linkwalker

import com.fasterxml.jackson.databind.ObjectMapper
import no.fint.linkwalker.dto.TestCase
import no.fint.linkwalker.dto.TestRequest
import no.fint.test.utils.MockMvcSpecification
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

class TestControllerSpec extends MockMvcSpecification {
    private MockMvc mockMvc
    private TestScheduler testScheduler
    private TestController controller

    void setup() {
        testScheduler = Mock(TestScheduler)
        controller = new TestController(testScheduler: testScheduler)
        mockMvc = standaloneSetup(controller)
    }

    def "Start test without including TestRequest"() {
        when:
        def response = mockMvc.perform(post('/tests'))

        then:
        response.andExpect(status().isBadRequest())
    }

    def "Start test with TestRequest"() {
        when:
        def request = new TestRequest(endpoint: '/test')
        def body = new ObjectMapper().writeValueAsString(request)
        def response = mockMvc.perform(post('/tests')
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(body))

        then:
        1 * testScheduler.scheduleTest(_ as String) >> new TestCase(UUID.randomUUID(), '/test')
        response.andExpect(status().isCreated())
    }
}
