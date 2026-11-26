package fr.vlegall.seal.ws.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionBindingTest {

    private static final Instant AT = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    @DisplayName("TRN-12, ADR 0044 : les anciens identifiants vont du plus récent au plus ancien, quatre au plus")
    void trn12FormersAreBoundedNewestFirst() {
        SessionBinding binding = SessionBinding.of("s0");
        for (int i = 1; i <= 6; i++) {
            binding = binding.renamedTo("s" + i, AT);
        }

        assertThat(binding).isEqualTo(new SessionBinding("s6", List.of("s5", "s4", "s3", "s2"), AT));
    }

    @Test
    @DisplayName(
            "TRN-12, AUTH-53 : la liaison nomme son identifiant actuel et ses anciens, puis oublie les anciens une fois confirmée")
    void trn12NamesAndSettles() {
        SessionBinding binding = SessionBinding.of("a0").renamedTo("a1", AT);

        assertThat(binding.names("a0")).isTrue();
        assertThat(binding.names("a1")).isTrue();
        assertThat(binding.names("autre")).isFalse();
        assertThat(binding.renamedAt()).isEqualTo(AT);
        assertThat(binding.settled()).isEqualTo(SessionBinding.of("a1"));
        assertThat(binding.settled().renamedAt()).isNull();
        assertThat(binding.settled().names("a0")).isFalse();
    }
}
