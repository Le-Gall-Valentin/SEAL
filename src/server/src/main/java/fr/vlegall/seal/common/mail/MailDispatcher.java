package fr.vlegall.seal.common.mail;

import fr.vlegall.seal.common.BackgroundTasks;
import fr.vlegall.seal.common.SealProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envoie les courriels de SEAL après la validation de la transaction qui les décide, hors de la requête, par les
 * {@link BackgroundTasks} (ADR 0019, point 8) : une transaction annulée n'envoie rien, un serveur SMTP lent ne retient
 * pas la requête, et l'identifiant de corrélation suit le courriel. Un échec n'est journalisé qu'avec le type de
 * l'erreur, dont le message peut citer l'adresse (SEC-21).
 */
@Component
public final class MailDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);

    private final JavaMailSender sender;
    private final String from;
    private final BackgroundTasks background;

    public MailDispatcher(JavaMailSender sender, SealProperties seal, BackgroundTasks background) {
        this.sender = sender;
        this.from = seal.mail().from();
        this.background = background;
    }

    /**
     * Programme l'envoi après la validation de la transaction en cours, obligatoire : une portée sans vraie transaction
     * ({@code NEVER}, {@code SUPPORTS}) n'a rien à valider ni à annuler, et l'envoi partirait même en cas d'échec.
     */
    public void sendAfterCommit(MailMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("envoi de courriel hors transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                background.submit(() -> send(message));
            }
        });
    }

    private void send(MailMessage message) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            sender.send(mail);
        } catch (RuntimeException e) {
            LOG.warn("Échec de l'envoi du courriel : {}", e.getClass().getSimpleName());
        }
    }
}
