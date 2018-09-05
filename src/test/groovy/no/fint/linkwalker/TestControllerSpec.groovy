package no.fint.linkwalker

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import no.fint.linkwalker.dto.TestCase
import no.fint.linkwalker.dto.TestRequest
import no.fint.test.utils.MockMvcSpecification
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TestControllerSpec extends MockMvcSpecification {
    private MockMvc mockMvc
    private TestScheduler testScheduler
    private TestCaseRepository repository
    private TestController controller

    void setup() {
        testScheduler = Mock()
        repository = Mock()
        controller = new TestController(testScheduler: testScheduler, repository: repository)

        def objectMapper = new ObjectMapper()
        objectMapper.enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build()
    }

    def "Start test without including TestRequest"() {
        when:
        def response = mockMvc.perform(post('/tests/fake'))

        then:
        response.andExpect(status().isBadRequest())
    }

    def "Start test with TestRequest"() {
        given:
        def request = new TestRequest(endpoint: '/test')
        def body = new ObjectMapper().writeValueAsString(request)

        when:
        def response = mockMvc.perform(post('/tests/fake')
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(body))

        then:
        1 * testScheduler.scheduleTest('fake', request) >> new TestCase(request)
        response.andExpect(status().isCreated())
    }

    def "Get all test cases without relations in response body"() {
        given:
        def testCase = new TestCase(new TestRequest('http://localhost', '/test', 'fake', 'client'))

        when:
        def response = mockMvc.perform(get('/tests/fake'))

        then:
        1 * repository.allTestCases('fake') >> [testCase]
        response.andExpect(status().isOk())
                .andExpect(jsonPathEquals('$[0].id', testCase.id.toString()))
                .andExpect(jsonPath('$[0].relations').doesNotExist())
    }

    def "Get single test case with relations in response body"() {
        given:
        def testCase = new TestCase(new TestRequest('http://localhost', '/test', 'fake', 'client'))

        when:
        def response = mockMvc.perform(get('/tests/fake/{id}', testCase.id.toString()))

        then:
        1 * repository.getCaseForId('fake', testCase.id) >> testCase
        response.andExpect(status().isOk())
                .andExpect(jsonPathEquals('$.id', testCase.id.toString()))
                .andExpect(jsonPath('$.relations').exists())
    }
}
