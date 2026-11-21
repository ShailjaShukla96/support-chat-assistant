package com.caramelai.support.api;

import static com.caramelai.support.support.ContractSupport.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class ChatApiTest {

    @Autowired MockMvc mvc;
    @Autowired FakeChatModel model;

    @BeforeEach
    void reset() {
        model.reset();
    }

    private String createSessionId() throws Exception {
        String body = mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.sessionId");
        return id;
    }

    @Test
    void createSessionReturns201MatchingTheContract() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"userRef\":\"host-user-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(matchesContract());
    }

    @Test
    void createSessionWithoutBodyIsAccepted() throws Exception {
        mvc.perform(post("/api/sessions")).andExpect(status().isCreated());
    }

    @Test
    void userRefOver128CharsIsRejected() throws Exception {
        mvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userRef\":\"" + "u".repeat(129) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void sendMessageReturnsAnAssistantReplyMatchingTheContract() throws Exception {
        model.thenReply("A 429 means you reached the rate limit.");
        String sessionId = createSessionId();

        mvc.perform(post("/api/sessions/{id}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Why 429?\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.text").value("A 429 means you reached the rate limit."))
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.escalationOffered").value(false))
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(matchesContract());
    }

    @Test
    void providerFailureIsAHandled200WithUnavailableOutcome() throws Exception {
        model.thenThrow(new RuntimeException("upstream 503 secret-detail"));
        String sessionId = createSessionId();

        String body = mvc.perform(post("/api/sessions/{id}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.escalationOffered").value(true))
                .andExpect(matchesContract())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("secret-detail").doesNotContain("503");
    }

    @Test
    void emptyWhitespaceAndOverlongTextAreRejectedWithProblemJsonAndNoModelCall() throws Exception {
        String sessionId = createSessionId();
        for (String text : new String[] {"", "   ", "x".repeat(2001)}) {
            mvc.perform(post("/api/sessions/{id}/messages", sessionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"" + text + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }
        mvc.perform(post("/api/sessions/{id}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(model.callCount()).isZero();
    }

    @Test
    void unknownSessionReturns404ProblemJson() throws Exception {
        mvc.perform(post("/api/sessions/{id}/messages", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void inboundRequestIdIsEchoed() throws Exception {
        mvc.perform(post("/api/sessions").header("X-Request-Id", "client-req-42"))
                .andExpect(header().string("X-Request-Id", "client-req-42"));
    }
}
