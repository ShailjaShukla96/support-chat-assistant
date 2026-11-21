package com.caramelai.support.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.api.dto.AssistantReply;
import com.caramelai.support.chat.ChatService;
import com.caramelai.support.persistence.Outcome;
import com.caramelai.support.support.RequiresLiveKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Spike S3 and the behavior acceptance checks (SC-006, FR-010, FR-016) against the real model. */
@Tag("live")
@RequiresLiveKey
@SpringBootTest
@ActiveProfiles("live")
class AssistantBehaviorLiveTest {

    private static final List<String> OUT_OF_KNOWLEDGE = List.of(
            "Does Caramel AI offer a quantum computing API?",
            "What is the price per million tokens for your largest model?",
            "How do I set up SSO with Okta for my team?",
            "Which data centers in Singapore do you run?",
            "Can I fine-tune a model on my own dataset?",
            "What is the SLA uptime percentage for enterprise customers?",
            "Do you support image generation endpoints?",
            "How do I export my invoices as PDF?",
            "Is there a Rust SDK for Caramel AI?",
            "What is your company's refund policy for unused credits?");

    @Autowired ChatService chat;

    private AssistantReply ask(String question) throws InterruptedException {
        Thread.sleep(1500); // be gentle with the provider quota
        UUID session = chat.createSession(null).sessionId();
        return chat.sendMessage(session, question);
    }

    @Test
    void s3_questionsOutsideTheSupportContentGetTheNoAnswerOutcome() throws Exception {
        int noAnswer = 0;
        StringBuilder report = new StringBuilder();
        for (String q : OUT_OF_KNOWLEDGE) {
            AssistantReply reply = ask(q);
            if (reply.outcome() == Outcome.NO_ANSWER) {
                noAnswer++;
            } else {
                report.append("\n  ").append(reply.outcome()).append(" <- ").append(q).append(" :: ").append(reply.text());
            }
        }
        assertThat(noAnswer)
                .as("S3 needs >= 9 of 10 NO_ANSWER; tighten system-prompt.txt if not. Misses:%s", report)
                .isGreaterThanOrEqualTo(9);
    }

    @Test
    void groundedQuestionIsAnsweredFromTheSupportContent() throws Exception {
        AssistantReply reply = ask("Why do I get HTTP 429 and what should I do?");
        assertThat(reply.outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(reply.text()).containsIgnoringCase("retry");
        assertThat(reply.escalationOffered()).isFalse();
    }

    @Test
    void nonEnglishInputGetsAnEnglishReplyStatingEnglishOnlySupport() throws Exception {
        AssistantReply reply = ask("¿Por qué recibo un error 429 al llamar a la API?");
        assertThat(reply.text()).containsIgnoringCase("english");
    }

    @Test
    void billingChangeRequestIsDeclinedWithAHumanOffer() throws Exception {
        AssistantReply reply = ask("Refund my last invoice and downgrade my plan.");
        assertThat(reply.text()).matches("(?is).*(can't|cannot|unable|not able|don't have).*");
        assertThat(reply.text()).matches("(?is).*(human|agent|support team).*");
        assertThat(reply.escalationOffered()).as("the UI must show the escalation action").isTrue();
    }
}
