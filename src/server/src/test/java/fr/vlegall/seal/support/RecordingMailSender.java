package fr.vlegall.seal.support;

import fr.vlegall.seal.security.logging.CorrelationIdFilter;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Faux serveur SMTP des tests : garde les courriels au lieu de les envoyer, et peut simuler une panne. Remplace le
 * {@code JavaMailSender} de Spring Boot dans le contexte de {@link SealIntegrationTest}, où les courriels partent sur
 * le fil de la requête ({@link MailTestConfiguration}) : ils sont donc tous là, ou absents pour de bon, dès que la
 * requête a répondu.
 */
public final class RecordingMailSender implements JavaMailSender {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
    private final List<String> correlationIds = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable MailException> failure = new AtomicReference<>();

    /** Courriels reçus, dans l'ordre d'envoi. */
    public List<SimpleMailMessage> sent() {
        return List.copyOf(sent);
    }

    /** Courriels adressés à {@code recipient}. */
    public List<SimpleMailMessage> sentTo(String recipient) {
        return sent.stream()
                .filter(message -> List.of(message.getTo() == null ? new String[0] : message.getTo())
                        .contains(recipient))
                .toList();
    }

    /** Identifiant de corrélation en vigueur au moment de chaque envoi (SEC-21). */
    public List<String> correlationIds() {
        return List.copyOf(correlationIds);
    }

    /** Fait échouer chaque envoi, jusqu'au prochain {@link #reset()}. */
    public void failWith(MailException exception) {
        failure.set(exception);
    }

    public void reset() {
        sent.clear();
        correlationIds.clear();
        failure.set(null);
    }

    @Override
    public void send(SimpleMailMessage... messages) {
        MailException exception = failure.get();
        if (exception != null) {
            throw exception;
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        correlationIds.add(correlationId == null ? "" : correlationId);
        sent.addAll(List.of(messages));
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        throw new UnsupportedOperationException("SEAL n'envoie que du texte brut");
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
        throw new UnsupportedOperationException("SEAL n'envoie que du texte brut");
    }
}
