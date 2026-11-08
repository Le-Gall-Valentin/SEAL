package fr.vlegall.seal.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;

@SealIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class MailDispatcherIT {

    private static final MailMessage MESSAGE = new MailMessage(
            "valentin.mail@exemple.ca", "Votre code de vérification SEAL", "Votre code de vérification SEAL : 482913.");

    @Autowired
    private MailDispatcher mail;

    @Autowired
    private RecordingMailSender sender;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    @AfterEach
    void reset() {
        sender.reset();
    }

    @Test
    @DisplayName("AUTH-14 : le courriel part après la validation de la transaction, depuis l'adresse de SEAL")
    void auth14MailIsSentAfterCommit() {
        transactions.executeWithoutResult(status -> {
            mail.sendAfterCommit(MESSAGE);
            assertThat(sender.sent()).as("rien ne part avant la validation").isEmpty();
        });

        SimpleMailMessage sent = sender.awaitSent(1).getFirst();
        assertThat(sent.getTo()).containsExactly("valentin.mail@exemple.ca");
        assertThat(sent.getFrom()).isEqualTo("SEAL <noreply@seal.localhost>");
        assertThat(sent.getSubject()).isEqualTo("Votre code de vérification SEAL");
        assertThat(sent.getText()).isEqualTo("Votre code de vérification SEAL : 482913.");
    }

    @Test
    @DisplayName("AUTH-14 : une transaction annulée n'envoie aucun courriel")
    void auth14RolledBackTransactionSendsNothing() {
        transactions.executeWithoutResult(status -> {
            mail.sendAfterCommit(MESSAGE);
            status.setRollbackOnly();
        });

        Awaitility.await()
                .during(Duration.ofMillis(300))
                .atMost(Duration.ofSeconds(1))
                .until(() -> sender.sent().isEmpty());
    }

    @Test
    @DisplayName("AUTH-14 : hors d'une transaction, l'envoi est refusé (sinon il partirait même en cas d'échec)")
    void auth14SendingOutsideATransactionIsRefused() {
        assertThatIllegalStateException().isThrownBy(() -> mail.sendAfterCommit(MESSAGE));
    }

    @Test
    @DisplayName("SEC-21 : un échec SMTP est journalisé sans le destinataire ni le corps du courriel")
    void sec21SmtpFailureLogsNeitherRecipientNorBody(CapturedOutput output) {
        sender.failWith(new MailSendException("Invalid Addresses: valentin.mail@exemple.ca"));

        transactions.executeWithoutResult(status -> mail.sendAfterCommit(MESSAGE));

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> output.getOut().contains("Échec de l'envoi du courriel"));
        assertThat(output.getAll())
                .contains("MailSendException")
                .doesNotContain("valentin.mail")
                .doesNotContain("482913");
    }

    @Test
    @DisplayName("SEC-21 : toString() d'un courriel ne révèle ni le destinataire ni le corps")
    void sec21MessageToStringHidesRecipientAndBody() {
        assertThat(MESSAGE.toString()).doesNotContain("valentin.mail").doesNotContain("482913");
    }
}
