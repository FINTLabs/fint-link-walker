package no.fint;

public enum Status {
    NOT_QUEUED, // this test is created, but not even queued
    NOT_STARTED, // this test is scheduled, but not yet started
    RUNNING, // this test is still running
    FAILED, // this url did not return 200 OK
    OK // this url returned 200 OK.
}
