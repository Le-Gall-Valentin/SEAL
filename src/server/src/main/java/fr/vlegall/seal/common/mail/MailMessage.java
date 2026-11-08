package fr.vlegall.seal.common.mail;

import java.util.Objects;

/**
 * Courriel en texte brut. {@link #toString()} ne révèle ni le destinataire ni le corps, qui peut porter un code
 * (SEC-21).
 */
public record MailMessage(String to, String subject, String body) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String toString() {
        return "MailMessage[to=***, subject=" + subject + "]";
    }
}
