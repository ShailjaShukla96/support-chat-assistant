package com.caramelai.support.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends ListCrudRepository<Message, UUID> {

    List<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * Newest first. Only messages that belong in the model's context: user messages (null outcome) and assistant
     * answers. Failed, blocked, and escalation-confirmation turns are never replayed to the model.
     */
    @Query("""
            SELECT * FROM MESSAGE
            WHERE SESSION_ID = :sessionId
              AND (OUTCOME IS NULL OR OUTCOME IN ('ANSWERED', 'NO_ANSWER'))
            ORDER BY CREATED_AT DESC
            LIMIT :limit
            """)
    List<Message> findRecentForPrompt(@Param("sessionId") UUID sessionId, @Param("limit") int limit);
}
