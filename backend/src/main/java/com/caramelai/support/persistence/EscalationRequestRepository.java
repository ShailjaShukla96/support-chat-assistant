package com.caramelai.support.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.ListCrudRepository;

public interface EscalationRequestRepository extends ListCrudRepository<EscalationRequest, UUID> {

    List<EscalationRequest> findAllByOrderByCreatedAtDesc(Limit limit);
}
