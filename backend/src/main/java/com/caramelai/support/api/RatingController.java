package com.caramelai.support.api;

import com.caramelai.support.api.dto.RatingRequest;
import com.caramelai.support.api.dto.RatingResponse;
import com.caramelai.support.rating.RatingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages/{messageId}/rating")
public class RatingController {

    private final RatingService ratings;

    public RatingController(RatingService ratings) {
        this.ratings = ratings;
    }

    @PutMapping
    public RatingResponse rate(@PathVariable UUID messageId, @RequestBody RatingRequest request) {
        return ratings.rate(messageId, request.value());
    }
}
