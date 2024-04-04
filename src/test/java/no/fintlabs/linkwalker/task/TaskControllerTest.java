package no.fintlabs.linkwalker.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskControllerTest {

    private TaskController controller;

    @BeforeEach
    void setUp() {
        controller = new TaskController();
    }

    @Test
    void valid_request() {
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
    void missing_required_body_and_header() {
        Task task = Task.builder()
                .url("https://example.com")
                .build();

        assertTrue(controller.requestNotValid(task, null));
    }

    @Test
    void valid_request_header_only() {
        Task task = Task.builder()
                .url("https://example.com")
                .build();

        assertFalse(controller.requestNotValid(task, "Am I the bearer of token? Yes, I am."));
    }

}
