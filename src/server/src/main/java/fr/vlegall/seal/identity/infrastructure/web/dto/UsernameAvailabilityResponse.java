package fr.vlegall.seal.identity.infrastructure.web.dto;

import fr.vlegall.seal.identity.domain.model.UsernameAvailability;
import java.util.List;

/** Corps de {@code GET /api/v1/auth/username-availability} (AUTH-05). */
public record UsernameAvailabilityResponse(boolean available, List<String> suggestions) {

    public UsernameAvailabilityResponse {
        suggestions = List.copyOf(suggestions);
    }

    public static UsernameAvailabilityResponse of(UsernameAvailability availability) {
        return new UsernameAvailabilityResponse(availability.available(), availability.suggestions());
    }
}
