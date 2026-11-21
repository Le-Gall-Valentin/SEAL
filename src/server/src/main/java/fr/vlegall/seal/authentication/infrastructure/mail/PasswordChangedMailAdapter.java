package fr.vlegall.seal.authentication.infrastructure.mail;

import fr.vlegall.seal.authentication.domain.port.out.PasswordChangedMailPort;
import fr.vlegall.seal.common.mail.MailDispatcher;
import fr.vlegall.seal.common.mail.MailMessage;
import org.springframework.stereotype.Component;

/** ACC-01 : courriel du changement de mot de passe, au texte de l'ADR 0034 (point 5), en texte brut (ADR 0019). */
@Component
public class PasswordChangedMailAdapter implements PasswordChangedMailPort {

    static final String SUBJECT = "Votre mot de passe SEAL a été changé";

    private final MailDispatcher mail;

    public PasswordChangedMailAdapter(MailDispatcher mail) {
        this.mail = mail;
    }

    @Override
    public void sendPasswordChanged(String email, String username) {
        String body = "Le mot de passe de votre compte SEAL (identifiant : " + username + ") vient d'être changé."
                + " Si ce n'est pas vous, utilisez « Mot de passe oublié » sur la page de connexion.";
        mail.sendAfterCommit(new MailMessage(email, SUBJECT, body));
    }
}
