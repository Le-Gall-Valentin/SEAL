package fr.vlegall.seal.identity.infrastructure.mail;

import fr.vlegall.seal.common.mail.MailDispatcher;
import fr.vlegall.seal.common.mail.MailMessage;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import org.springframework.stereotype.Component;

/**
 * Textes des courriels d'{@code identity}, en français et en texte brut (ADR 0019, point 8). Aucune donnée saisie n'y
 * figure, hormis l'identifiant, dont AUTH-01 borne les caractères.
 */
@Component
public class AccountMailAdapter implements AccountMailPort {

    static final String CODE_SUBJECT = "Votre code de vérification SEAL";
    static final String WARNING_SUBJECT = "Tentative d'inscription sur SEAL";
    static final String WARNING_BODY =
            "Quelqu'un a tenté de créer un compte SEAL avec cette adresse, déjà associée à un"
                    + " compte. Si c'était vous, connectez-vous ou utilisez « Mot de passe oublié ». Votre compte n'est pas"
                    + " modifié.";

    private final MailDispatcher mail;

    public AccountMailAdapter(MailDispatcher mail) {
        this.mail = mail;
    }

    @Override
    public void sendCode(EmailAddress to, Username username, VerificationCode code) {
        String body = "Votre code de vérification SEAL : " + code.value() + ". Il expire dans "
                + CodePolicy.VALIDITY.toMinutes() + " minutes. Votre identifiant : " + username.value()
                + ". Si vous n'avez pas créé de compte SEAL, ignorez ce message.";
        mail.sendAfterCommit(new MailMessage(to.value(), CODE_SUBJECT, body));
    }

    @Override
    public void sendExistingAccountWarning(EmailAddress to) {
        mail.sendAfterCommit(new MailMessage(to.value(), WARNING_SUBJECT, WARNING_BODY));
    }
}
