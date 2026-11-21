package com.caramelai.support.api;

import static com.caramelai.support.support.ContractSupport.matchesContract;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class RatingApiTest {

    @Autowired MockMvc mvc;
    @Autowired FakeChatModel model;

    @BeforeEach
    void reset() {
        model.reset().thenReply("A 429 means the rate limit.");
    }

    private String assistantMessageId() throws Exception {
        String session = JsonPath.read(
                mvc.perform(post("/api/sessions")).andReturn().getResponse().getContentAsString(), "$.sessionId");
        String body = mvc.perform(post("/api/sessions/{id}/messages", session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Why 429?\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.messageId");
    }

    private org.springframework.test.web.servlet.ResultActions rate(String messageId, String json) throws Exception {
        return mvc.perform(put("/api/messages/{id}/rating", messageId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void upAndDownReturn200MatchingTheContract() throws Exception {
        String id = assistantMessageId();
        rate(id, "{\"value\":\"UP\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(id))
                .andExpect(jsonPath("$.value").value("UP"))
                .andExpect(matchesContract());
        rate(id, "{\"value\":\"DOWN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("DOWN"))
                .andExpect(matchesContract());
    }

    @Test
    void otherValuesAreRejected() throws Exception {
        String id = assistantMessageId();
        for (String bad : new String[] {"{\"value\":\"MEH\"}", "{\"value\":\"up\"}", "{}", "{\"value\":null}"}) {
            rate(id, bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
    }

    @Test
    void unknownMessageReturns404() throws Exception {
        rate(UUID.randomUUID().toString(), "{\"value\":\"UP\"}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void userMessageReturns400() throws Exception {
        // a user message ID: send a message, then look it up through a second call is not exposed, so use the
        // service-level guarantee here and check the HTTP mapping with a known user message id
        String session = JsonPath.read(
                mvc.perform(post("/api/sessions")).andReturn().getResponse().getContentAsString(), "$.sessionId");
        mvc.perform(post("/api/sessions/{id}/messages", session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\"}"));
        String userMessageId = userMessageIdOf(session);

        rate(userMessageId, "{\"value\":\"UP\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Autowired com.caramelai.support.persistence.MessageRepository messages;

    private String userMessageIdOf(String sessionId) {
        return messages.findBySessionIdOrderByCreatedAtAsc(UUID.fromString(sessionId)).stream()
                .filter(m -> m.role() == com.caramelai.support.persistence.Role.USER)
                .findFirst()
                .orElseThrow()
                .id()
                .toString();
    }
}
