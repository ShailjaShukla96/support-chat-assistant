package com.caramelai.support.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

public interface ResponseRatingRepository extends ListCrudRepository<ResponseRating, UUID> {

    Optional<ResponseRating> findByMessageId(UUID messageId);
}
