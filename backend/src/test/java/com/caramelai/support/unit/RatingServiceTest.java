package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caramelai.support.api.InvalidMessageException;
import com.caramelai.support.api.SessionNotFoundException;
import com.caramelai.support.api.dto.AssistantReply;
import com.caramelai.support.api.dto.RatingResponse;
import com.caramelai.support.chat.ChatService;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.RatingValue;
import com.caramelai.support.persistence.ResponseRatingRepository;
import com.caramelai.support.rating.RatingService;
import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class RatingServiceTest {

    @Autowired RatingService ratings;
    @Autowired ChatService chat;
    @Autowired FakeChatModel model;
    @Autowired ResponseRatingRepository ratingRepository;
    @Autowired MessageRepository messages;

    @BeforeEach
    void reset() {
        model.reset();
    }

    private AssistantReply reply() {
        UUID s = chat.createSession(null).sessionId();
        return chat.sendMessage(s, "Why 429?");
    }

    @Test
    void firstRatingCreatesARow() {
        AssistantReply r = reply();

        RatingResponse result = ratings.rate(r.messageId(), RatingValue.UP);

        assertThat(result.messageId()).isEqualTo(r.messageId());
        assertThat(result.value()).isEqualTo(RatingValue.UP);
        assertThat(result.updatedAt()).isNotNull();
        assertThat(ratingRepository.findByMessageId(r.messageId())).isPresent();
    }

    @Test
    void aRepeatRatingReplacesTheValueOnTheSameRow() throws Exception {
        AssistantReply r = reply();
        RatingResponse first = ratings.rate(r.messageId(), RatingValue.UP);
        UUID rowId = ratingRepository.findByMessageId(r.messageId()).orElseThrow().id();
        Thread.sleep(5);

        RatingResponse second = ratings.rate(r.messageId(), RatingValue.DOWN);

        var stored = ratingRepository.findByMessageId(r.messageId()).orElseThrow();
        assertThat(stored.id()).isEqualTo(rowId);
        assertThat(stored.value()).isEqualTo(RatingValue.DOWN);
        assertThat(second.updatedAt()).isAfter(first.updatedAt());
        assertThat(ratingRepository.findAll().stream().filter(x -> x.messageId().equals(r.messageId())).count())
                .isEqualTo(1);
    }

    @Test
    void ratingCanSwitchBackAndForth() {
        AssistantReply r = reply();
        ratings.rate(r.messageId(), RatingValue.UP);
        ratings.rate(r.messageId(), RatingValue.DOWN);
        ratings.rate(r.messageId(), RatingValue.UP);
        assertThat(ratingRepository.findByMessageId(r.messageId()).orElseThrow().value()).isEqualTo(RatingValue.UP);
    }

    @Test
    void ratingAUserMessageIsRejected() {
        AssistantReply r = reply();
        UUID userMessageId = messages.findAll().stream()
                .filter(m -> m.role().name().equals("USER"))
                .max(java.util.Comparator.comparing(m -> m.createdAt()))
                .orElseThrow()
                .id();

        assertThatThrownBy(() -> ratings.rate(userMessageId, RatingValue.UP)).isInstanceOf(InvalidMessageException.class);
        assertThat(ratingRepository.findByMessageId(userMessageId)).isEmpty();
        assertThat(r).isNotNull();
    }

    @Test
    void unknownMessageIsNotFound() {
        assertThatThrownBy(() -> ratings.rate(UUID.randomUUID(), RatingValue.UP))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void missingValueIsRejected() {
        AssistantReply r = reply();
        assertThatThrownBy(() -> ratings.rate(r.messageId(), null)).isInstanceOf(InvalidMessageException.class);
    }
}
