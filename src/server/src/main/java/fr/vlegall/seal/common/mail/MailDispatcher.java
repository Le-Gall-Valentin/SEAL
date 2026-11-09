package fr.vlegall.seal.common.mail;

import fr.vlegall.seal.common.SealProperties;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envoie les courriels de SEAL après la validation de la transaction qui les décide, par un {@link MailExecutor}
 * (ADR 0019, point 8) : une transaction annulée n'envoie rien. Un échec n'est journalisé qu'avec le type de l'erreur,
 * dont le message peut citer l'adresse (SEC-21), et l'identifiant de corrélation de la requête.
 */
@Component
public final class MailDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);

    private final JavaMailSender sender;
    private final String from;
    private final MailExecutor executor;

    public MailDispatcher(JavaMailSender sender, SealProperties seal, MailExecutor executor) {
        this.sender = sender;
        this.from = seal.mail().from();
        this.executor = executor;
    }

    /** Programme l'envoi après la validation de la transaction en cours, obligatoire. */
    public void sendAfterCommit(MailMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("envoi de courriel hors transaction");
        }
        // L'identifiant de corrélation de la requête suit le courriel sur le fil d'envoi (SEC-21).
        Map<String, String> context = MDC.getCopyOfContextMap();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(message, context);
            }
        });
    }

    private void submit(MailMessage message, @Nullable Map<String, String> context) {
        try {
            executor.execute(() -> send(message, context));
        } catch (RejectedExecutionException e) {
            LOG.warn("File d'envoi des courriels pleine : courriel non envoyé");
        }
    }

    private void send(MailMessage message, @Nullable Map<String, String> context) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        restore(context);
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            sender.send(mail);
        } catch (RuntimeException e) {
            LOG.warn("Échec de l'envoi du courriel : {}", e.getClass().getSimpleName());
        } finally {
            // Le fil d'envoi retrouve son propre contexte, qu'il soit dédié ou celui de la requête.
            restore(previous);
        }
    }

    private static void restore(@Nullable Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
