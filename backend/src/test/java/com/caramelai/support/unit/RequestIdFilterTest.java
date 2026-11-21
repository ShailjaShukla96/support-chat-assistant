package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.config.RequestIdFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private record Result(String header, String mdcDuringRequest) {}

    private Result run(String inbound) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        if (inbound != null) {
            request.addHeader("X-Request-Id", inbound);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));
        return new Result(response.getHeader("X-Request-Id"), seen.get());
    }

    @Test
    void validInboundIdIsReused() throws Exception {
        Result r = run("abc-123_XYZ");
        assertThat(r.header()).isEqualTo("abc-123_XYZ");
        assertThat(r.mdcDuringRequest()).isEqualTo("abc-123_XYZ");
    }

    @Test
    void missingIdIsGenerated() throws Exception {
        Result r = run(null);
        assertThat(r.header()).isNotBlank().hasSizeLessThanOrEqualTo(64);
        assertThat(r.mdcDuringRequest()).isEqualTo(r.header());
    }

    @Test
    void invalidIdsAreReplaced() throws Exception {
        assertThat(run("bad id with spaces").header()).isNotEqualTo("bad id with spaces");
        assertThat(run("x".repeat(65)).header()).hasSizeLessThanOrEqualTo(64).isNotEqualTo("x".repeat(65));
        assertThat(run("evil\r\nheader: 1").header()).doesNotContain("\n");
    }

    @Test
    void mdcIsClearedAfterTheRequest() throws Exception {
        run("abc");
        assertThat(MDC.get("requestId")).isNull();
    }
}
