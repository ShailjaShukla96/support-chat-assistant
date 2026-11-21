package com.caramelai.support.api;

import static com.caramelai.support.support.ContractSupport.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import com.jayway.jsonpath.JsonPath;
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
class EscalationApiTest {

    private static final String TOKEN = "test-agent-token";

    @Autowired MockMvc mvc;
    @Autowired FakeChatModel model;

    @BeforeEach
    void reset() {
        model.reset();
    }

    private String newSession() throws Exception {
        String body = mvc.perform(post("/api/sessions")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.sessionId");
    }

    private String escalate(String sessionId, String reasonJson) throws Exception {
        var req = post("/api/sessions/{id}/escalations", sessionId).contentType(MediaType.APPLICATION_JSON);
        if (reasonJson != null) {
            req = req.content(reasonJson);
        }
        String body = mvc.perform(req)
                .andExpect(status().isCreated())
                .andExpect(matchesContract())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.escalationId");
    }

    @Test
    void escalationReturns201ReceiptWithDefaultReason() throws Exception {
        String session = newSession();
        mvc.perform(post("/api/sessions/{id}/escalations", session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.escalationId").exists())
                .andExpect(jsonPath("$.receivedAt").exists())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(matchesContract());
    }

    @Test
    void escalationWithExplicitReasonIsAccepted() throws Exception {
        escalate(newSession(), "{\"reason\":\"ASSISTANT_UNSURE\"}");
    }

    @Test
    void unknownSessionReturns404() throws Exception {
        mvc.perform(post("/api/sessions/{id}/escalations", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void agentEndpointsRejectMissingOrWrongToken() throws Exception {
        mvc.perform(get("/api/agent/escalations"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/agent/escalations").header("X-Agent-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agent/escalations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listIsNewestFirstWithoutTranscriptsAndHonorsLimit() throws Exception {
        String s1 = newSession();
        String s2 = newSession();
        String first = escalate(s1, null);
        Thread.sleep(5);
        String second = escalate(s2, null);

        mvc.perform(get("/api/agent/escalations").header("X-Agent-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].escalationId").value(second))
                .andExpect(jsonPath("$[0].transcript").doesNotExist())
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(matchesContract());

        mvc.perform(get("/api/agent/escalations").param("limit", "1").header("X-Agent-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].escalationId").value(second));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void limitOutsideOneTo200IsRejected() throws Exception {
        mvc.perform(get("/api/agent/escalations").param("limit", "0").header("X-Agent-Token", TOKEN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/agent/escalations").param("limit", "201").header("X-Agent-Token", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailReturnsTheFullTranscript() throws Exception {
        model.thenReply("Reply one.");
        String session = newSession();
        mvc.perform(post("/api/sessions/{id}/messages", session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Question one\"}"))
                .andExpect(status().isOk());
        String escalationId = escalate(session, null);

        mvc.perform(get("/api/agent/escalations/{id}", escalationId).header("X-Agent-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalationId").value(escalationId))
                .andExpect(jsonPath("$.sessionId").value(session))
                .andExpect(jsonPath("$.transcript.length()").value(2))
                .andExpect(jsonPath("$.transcript[0].role").value("USER"))
                .andExpect(jsonPath("$.transcript[0].text").value("Question one"))
                .andExpect(jsonPath("$.transcript[1].role").value("ASSISTANT"))
                .andExpect(matchesContract());
    }

    @Test
    void unknownEscalationReturns404() throws Exception {
        mvc.perform(get("/api/agent/escalations/{id}", UUID.randomUUID()).header("X-Agent-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void messageReplyForAFreeTextHumanRequestCarriesTheEscalationId() throws Exception {
        String session = newSession();
        mvc.perform(post("/api/sessions/{id}/messages", session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"I want to talk to a person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ESCALATED"))
                .andExpect(jsonPath("$.escalationId").exists())
                .andExpect(matchesContract());
        assertThat(model.callCount()).isZero();
    }
}
