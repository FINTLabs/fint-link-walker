package no.fint;

public enum Status {
    NOT_STARTED, // this test is scheduled, but not yet started
    RUNNING, // this test is still running
    FAILED, // this test, and/or at least one sub-startTest failed
    OK // test and all sub-tests ran ok
}
