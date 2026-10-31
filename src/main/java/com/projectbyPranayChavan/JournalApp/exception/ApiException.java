package com.projectbyPranayChavan.JournalApp.exception;

import org.springframework.http.HttpStatus;

/** A problem the client can understand and fix. The message is safe to show to the user. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
