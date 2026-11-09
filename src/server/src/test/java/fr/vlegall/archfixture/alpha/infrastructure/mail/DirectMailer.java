package fr.vlegall.archfixture.alpha.infrastructure.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Interdit : envoie directement, sans attendre la validation de la transaction (ADR 0019, point 8). */
public class DirectMailer {
    void send(JavaMailSender sender) {
        sender.send(new SimpleMailMessage());
    }
}
