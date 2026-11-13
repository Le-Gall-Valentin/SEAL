package fr.vlegall.seal.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import fr.vlegall.seal.common.BackgroundTasks;
import fr.vlegall.seal.common.BoundedBackgroundTasks;
import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.security.logging.CorrelationIdFilter;
import fr.vlegall.seal.support.RecordingMailSender;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MailDispatcherTest {

    private static final MailMessage MESSAGE =
            new MailMessage("valentin.mail@exemple.ca", "Objet", "Votre code de vérification SEAL : 482913.");

    private final RecordingMailSender sender = new RecordingMailSender();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        MDC.clear();
    }

    private MailDispatcher dispatcher(BackgroundTasks background) {
        SealProperties properties = new SealProperties(
                URI.create("https://localhost:8443"),
                new SealProperties.Security(Duration.ofMinutes(5)),
                new SealProperties.Session(Duration.ofHours(12)),
                new SealProperties.Mail("SEAL <noreply@seal.localhost>"));
        return new MailDispatcher(sender, properties, background);
    }

    /** Ouvre une transaction et sa synchronisation, comme le ferait le gestionnaire de transactions. */
    private static void begin() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    /** Simule la validation de la transaction : lance les synchronisations enregistrées. */
    private static void commit() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("SEC-21 : l'identifiant de corrélation de la requête suit le courriel sur le fil d'envoi")
    void sec21CorrelationIdFollowsTheMail() throws Exception {
        BoundedBackgroundTasks background = new BoundedBackgroundTasks();
        try {
            begin();
            MDC.put(CorrelationIdFilter.MDC_KEY, "requete-1");

            dispatcher(background).sendAfterCommit(MESSAGE);
            commit();
        } finally {
            background.destroy();
        }

        assertThat(sender.correlationIds()).containsExactly("requete-1");
    }

    @Test
    @DisplayName("SEC-21 : un envoi sur le fil appelant lui rend son contexte de journalisation")
    void sec21SendingOnTheCallerThreadKeepsItsContext() {
        begin();
        MDC.put(CorrelationIdFilter.MDC_KEY, "requete-1");

        dispatcher(Runnable::run).sendAfterCommit(MESSAGE);
        commit();

        assertThat(sender.sent()).hasSize(1);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("requete-1");
    }

    @Test
    @DisplayName(
            "AUTH-14 : une portée sans vraie transaction (NEVER, SUPPORTS) refuse l'envoi, comme l'absence de portée")
    void auth14ScopeWithoutActualTransactionRefusesTheMail() {
        TransactionSynchronizationManager.initSynchronization();

        assertThatIllegalStateException()
                .isThrownBy(() -> dispatcher(Runnable::run).sendAfterCommit(MESSAGE));
    }

    @Test
    @DisplayName("AUTH-14 : rien ne part avant la validation de la transaction (ADR 0019, point 8)")
    void auth14NothingLeavesBeforeTheCommit() {
        begin();

        dispatcher(Runnable::run).sendAfterCommit(MESSAGE);

        assertThat(sender.sent()).isEmpty();
        commit();
        assertThat(sender.sent()).hasSize(1);
    }
}
