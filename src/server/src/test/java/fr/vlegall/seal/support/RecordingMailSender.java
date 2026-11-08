package fr.vlegall.seal.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Faux serveur SMTP des tests : garde les courriels au lieu de les envoyer, et peut simuler une panne. Remplace le
 * {@code JavaMailSender} de Spring Boot dans le contexte de {@link SealIntegrationTest}.
 */
public final class RecordingMailSender implements JavaMailSender {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable MailException> failure = new AtomicReference<>();

    /** Courriels reçus, dans l'ordre d'envoi. */
    public List<SimpleMailMessage> sent() {
        return List.copyOf(sent);
    }

    /** Attend que {@code count} courriels au moins soient partis, puis les renvoie. */
    public List<SimpleMailMessage> awaitSent(int count) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> sent.size() >= count);
        return sent();
    }

    /** Courriels adressés à {@code recipient}. */
    public List<SimpleMailMessage> sentTo(String recipient) {
        return sent.stream()
                .filter(message -> List.of(message.getTo() == null ? new String[0] : message.getTo())
                        .contains(recipient))
                .toList();
    }

    /** Fait échouer chaque envoi, jusqu'au prochain {@link #reset()}. */
    public void failWith(MailException exception) {
        failure.set(exception);
    }

    public void reset() {
        sent.clear();
        failure.set(null);
    }

    @Override
    public void send(SimpleMailMessage... messages) {
        MailException exception = failure.get();
        if (exception != null) {
            throw exception;
        }
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
