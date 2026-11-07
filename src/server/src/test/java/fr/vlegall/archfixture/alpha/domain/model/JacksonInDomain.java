package fr.vlegall.archfixture.alpha.domain.model;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.json.JsonMapper;

/** Interdit : un domaine qui dépend de Jackson et de jakarta. */
public class JacksonInDomain {
    @NotNull
    String write(JsonMapper mapper, Object value) {
        return mapper.writeValueAsString(value);
    }
}
