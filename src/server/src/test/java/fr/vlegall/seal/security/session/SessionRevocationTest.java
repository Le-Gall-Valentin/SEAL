package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

@ExtendWith(OutputCaptureExtension.class)
class SessionRevocationTest {

    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<MapSession> sessions = mock(FindByIndexNameSessionRepository.class);

    private final SecurityJournal journal = mock(SecurityJournal.class);
    private final List<Object> published = new ArrayList<>();
    private final SessionRevocation revocation = new SessionRevocation(sessions, journal, published::add);
    private final UserId user = new UserId(UUID.randomUUID());

    @Test
    @DisplayName("TRN-12, ADR 0043 : une révocation annonce les sessions supprimées, pour fermer leurs WebSockets")
    void trn12RevocationAnnouncesTheEndedSessions() {
        when(sessions.findByPrincipalName(user.value().toString()))
                .thenReturn(Map.of("courante", new MapSession("courante"), "autre", new MapSession("autre")));

        revocation.revokeOthers(user, "courante");

        assertThat(published).containsExactly(new SessionsEndedEvent(Set.of("autre")));
    }

    @Test
    @DisplayName("TRN-12 : sans session à révoquer, rien n'est annoncé")
    void trn12NothingRevokedNothingAnnounced() {
        when(sessions.findByPrincipalName(user.value().toString()))
                .thenReturn(Map.of("courante", new MapSession("courante")));

        revocation.revokeOthers(user, "courante");

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("SEC-20 : la révocation tient même si le journal échoue, et l'échec est écrit en erreur (A10)")
    void sec20RevocationHoldsWhenTheJournalFails(CapturedOutput output) {
        when(sessions.findByPrincipalName(user.value().toString()))
                .thenReturn(Map.of("courante", new MapSession("courante"), "autre", new MapSession("autre")));
        doThrow(new IllegalStateException("base indisponible")).when(journal).recordStandalone(any());

        assertThatNoException().isThrownBy(() -> revocation.revokeOthers(user, "courante"));

        verify(sessions).deleteById("autre");
        verify(sessions, never()).deleteById("courante");
        assertThat(output).contains("Journal de sécurité indisponible : révocation de sessions non enregistrée");
    }
}
