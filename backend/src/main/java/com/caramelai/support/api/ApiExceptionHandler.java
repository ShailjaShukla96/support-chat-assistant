package com.caramelai.support.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps failures to RFC 9457 problem responses. Bodies never carry stack traces, keys, or provider errors. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidMessageException.class)
    public ProblemDetail invalidMessage(InvalidMessageException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail malformed(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request body is missing or invalid.");
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail notFound(SessionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        // Log the class only: the message could embed internal details.
        log.error("Unhandled error: {}", e.getClass().getName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "Something went wrong. Please try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}
