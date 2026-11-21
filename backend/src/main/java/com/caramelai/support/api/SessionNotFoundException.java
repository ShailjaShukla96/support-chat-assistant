package com.caramelai.support.api;

/** A session, message, or escalation was not found. The message is safe to show. */
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }
}
