package no.fint.linkwalker.dto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum Status {
    NOT_QUEUED, // this test is created, but not even queued
    NOT_STARTED, // this test is scheduled, but not yet started
    RUNNING, // this test is still running
    FAILED, // this url did not return 200 OK
    OK; // this url returned 200 OK.

    public static Status get(String status) {
        try {
            return Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Status '{}' not found, returning FAILED", status);
            return Status.FAILED;
        }
    }
}
