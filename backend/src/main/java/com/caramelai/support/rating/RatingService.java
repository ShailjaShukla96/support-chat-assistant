package com.caramelai.support.rating;

import com.caramelai.support.api.InvalidMessageException;
import com.caramelai.support.api.SessionNotFoundException;
import com.caramelai.support.api.dto.RatingResponse;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.RatingValue;
import com.caramelai.support.persistence.ResponseRating;
import com.caramelai.support.persistence.ResponseRatingRepository;
import com.caramelai.support.persistence.Role;
import com.caramelai.support.persistence.Store;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stores the customer's latest rating for an assistant message (upsert, one row per message). */
@Service
public class RatingService {

    private final MessageRepository messages;
    private final ResponseRatingRepository ratings;
    private final Store store;

    public RatingService(MessageRepository messages, ResponseRatingRepository ratings, Store store) {
        this.messages = messages;
        this.ratings = ratings;
        this.store = store;
    }

    public RatingResponse rate(UUID messageId, RatingValue value) {
        if (value == null) {
            throw new InvalidMessageException("A rating value of UP or DOWN is required.");
        }
        Message message = messages.findById(messageId).orElseThrow(() -> new SessionNotFoundException("Message not found."));
        if (message.role() != Role.ASSISTANT) {
            throw new InvalidMessageException("Only assistant messages can be rated.");
        }
        Instant now = Instant.now();
        ResponseRating saved = ratings.findByMessageId(messageId)
                .map(existing -> store.update(new ResponseRating(existing.id(), messageId, value, now)))
                .orElseGet(() -> store.insert(new ResponseRating(UUID.randomUUID(), messageId, value, now)));
        return new RatingResponse(saved.messageId(), saved.value(), saved.updatedAt());
    }
}
