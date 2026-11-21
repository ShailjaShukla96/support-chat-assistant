package com.caramelai.support.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caramelai.support.support.TestChatConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** With no AGENT_API_TOKEN configured, every /api/agent/** path is disabled (404), whatever token is sent. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestChatConfig.class)
@TestPropertySource(properties = "app.agent.token=")
class AgentEndpointsDisabledTest {

    @Autowired MockMvc mvc;

    @Test
    void allAgentPathsReturn404WhenNoTokenIsConfigured() throws Exception {
        mvc.perform(get("/api/agent/escalations")).andExpect(status().isNotFound());
        mvc.perform(get("/api/agent/escalations").header("X-Agent-Token", ""))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/agent/escalations").header("X-Agent-Token", "anything"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/agent/escalations/{id}", UUID.randomUUID()).header("X-Agent-Token", "x"))
                .andExpect(status().isNotFound());
    }
}
