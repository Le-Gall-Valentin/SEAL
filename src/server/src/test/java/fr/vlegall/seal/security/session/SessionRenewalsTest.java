package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;

class SessionRenewalsTest {

    @Test
    @DisplayName("TRN-12, AUTH-55, ADR 0044 : tout renommage de Spring Security devient un SessionRenewedEvent")
    void trn12EverySpringRenameIsAnnounced() {
        List<Object> published = new ArrayList<>();
        SessionRenewals renewals = new SessionRenewals(published::add);

        renewals.renamed(new SessionFixationProtectionEvent(new TestingAuthenticationToken("oauth", null), "a0", "a1"));

        assertThat(published).containsExactly(new SessionRenewedEvent("a0", "a1"));
    }
}
