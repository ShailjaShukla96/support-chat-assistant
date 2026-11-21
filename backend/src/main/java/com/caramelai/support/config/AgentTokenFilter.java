package com.caramelai.support.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects {@code /api/agent/**} with a shared secret (research R7). With no token configured the endpoints are
 * disabled (404). Wrong or missing tokens get 401. Comparison is constant-time.
 */
@Component
public class AgentTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Agent-Token";

    private final AppProperties props;

    public AgentTokenFilter(AppProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/agent/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = props.agent().token();
        if (expected == null || expected.isBlank()) {
            problem(response, 404, "Not found", "Not found.");
            return;
        }
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            problem(response, 401, "Unauthorized", "A valid agent token is required.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void problem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
