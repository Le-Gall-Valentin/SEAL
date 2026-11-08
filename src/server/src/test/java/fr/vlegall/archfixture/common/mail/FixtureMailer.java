package fr.vlegall.archfixture.common.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Permis : common.mail est le seul à parler au serveur SMTP. */
public class FixtureMailer {
    void send(JavaMailSender sender) {
        sender.send(new SimpleMailMessage());
    }
}
