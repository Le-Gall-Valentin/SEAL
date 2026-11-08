package fr.vlegall.seal.common.mail;

import fr.vlegall.seal.common.SealProperties;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envoie les courriels de SEAL après la validation de la transaction qui les décide, sur un fil unique (ADR 0019,
 * point 8) : une transaction annulée n'envoie rien, et un serveur SMTP lent ne retient pas la requête. La file est
 * bornée (A06) ; un échec n'est journalisé qu'avec le type de l'erreur, dont le message peut citer l'adresse (SEC-21),
 * et l'identifiant de corrélation de la requête.
 */
@Component
public final class MailDispatcher implements DisposableBean {

    static final int QUEUE_CAPACITY = 1_000;

    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);

    private final JavaMailSender sender;
    private final String from;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "seal-mail");
                thread.setDaemon(true);
                return thread;
            });

    public MailDispatcher(JavaMailSender sender, SealProperties seal) {
        this.sender = sender;
        this.from = seal.mail().from();
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

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private void submit(MailMessage message, @Nullable Map<String, String> context) {
        try {
            executor.execute(() -> send(message, context));
        } catch (RejectedExecutionException e) {
            LOG.warn("File d'envoi des courriels pleine : courriel non envoyé");
        }
    }

    private void send(MailMessage message, @Nullable Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        }
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
            MDC.clear();
        }
    }
}
