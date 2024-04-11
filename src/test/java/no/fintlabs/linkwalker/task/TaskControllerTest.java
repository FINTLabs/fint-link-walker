package no.fintlabs.linkwalker.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class TaskControllerTest {

    private TaskController controller;

    @Mock
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        controller = new TaskController(taskService);
    }

    @Test
    void valid_request_with_everything() {
        Task task = Task.builder()
                .url("https://example.com")
                .clientName("clientName")
                .org("org")
                .build();

        assertFalse(controller.requestNotValid(task, "Bearer Token, nice to meet you."));
    }

    @Test
    void missing_url() {
        Task task = Task.builder()
                .clientName("clientName")
                .org("org")
                .build();

        assertTrue(controller.requestNotValid(task, "Hi im bearer token."));
    }

    @Test
    void missing_org() {
        Task task = Task.builder()
                .url("https://example.com")
                .clientName("clientName")
                .build();

        assertFalse(controller.requestNotValid(task, "Bearer Token, but missing org."));
    }

    @Test
    void missing_required_fields_without_auth_header() {
        Task task = Task.builder()
                .url("https://example.com")
                .build();

        assertTrue(controller.requestNotValid(task, null));
    }

    @Test
    void valid_request_with_header() {
        Task task = Task.builder()
                .url("https://example.com")
                .org("org")
                .build();

        assertFalse(controller.requestNotValid(task, "Am I the bearer of token? Yes, I am."));
    }

}
