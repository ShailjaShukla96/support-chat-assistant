package com.caramelai.support.api;

/** Customer input failed validation (empty, too long, bad value). The message is safe to show. */
public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String message) {
        super(message);
    }
}
