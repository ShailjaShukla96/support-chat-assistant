package com.caramelai.support.persistence;

import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

public interface ChatSessionRepository extends ListCrudRepository<ChatSession, UUID> {}
