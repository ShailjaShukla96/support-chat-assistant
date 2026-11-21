package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.api.ApiExceptionHandler;
import com.caramelai.support.api.InvalidMessageException;
import com.caramelai.support.api.SessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void invalidMessageMapsTo400WithoutLeakingInternals() {
        ProblemDetail p = handler.invalidMessage(new InvalidMessageException("Message must not be empty."));
        assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(p.getTitle()).isEqualTo("Invalid request");
        assertThat(p.getDetail()).isEqualTo("Message must not be empty.");
    }

    @Test
    void notFoundMapsTo404() {
        ProblemDetail p = handler.notFound(new SessionNotFoundException("Session not found."));
        assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(p.getTitle()).isEqualTo("Not found");
    }

    @Test
    void unexpectedErrorsHideTheCause() {
        ProblemDetail p = handler.unexpected(new IllegalStateException("jdbc:h2:file:secret-path password=x"));
        assertThat(p.getStatus()).isEqualTo(500);
        assertThat(p.getDetail()).doesNotContain("jdbc").doesNotContain("password");
    }
}
