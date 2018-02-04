package no.fint.linkwalker

import no.fint.test.utils.MockMvcSpecification
import org.springframework.test.web.servlet.MockMvc

class TestControllerSpec extends MockMvcSpecification {
    private MockMvc mockMvc
    private TestController controller

    void setup() {
        controller = new TestController()
        mockMvc = standaloneSetup(controller)
    }

    def "Start test"() {
        when:
        def response = mockMvc.perform(post('/tests'))

        then:
        response.andExpect(status().isOk())
    }
}
