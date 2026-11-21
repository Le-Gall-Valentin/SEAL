package fr.vlegall.seal.identity.infrastructure.mail;

import fr.vlegall.seal.common.mail.MailDispatcher;
import fr.vlegall.seal.common.mail.MailMessage;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import fr.vlegall.seal.security.logging.LogValues;
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

    static final String TAKEN_SUBJECT = "Tentative d'ajout de votre adresse sur SEAL";
    static final String TAKEN_BODY = "Quelqu'un a tenté d'associer cette adresse à un autre compte SEAL. Elle reste"
            + " associée à votre compte, qui n'est pas modifié.";
    static final String CHANGED_SUBJECT = "Votre courriel SEAL a été changé";

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

    /** ACC-02 : texte de l'ADR 0034 (point 5). */
    @Override
    public void sendEmailChangeCode(EmailAddress to, Username username, VerificationCode code) {
        String body = "Votre code de vérification SEAL : " + code.value() + ". Il expire dans "
                + CodePolicy.VALIDITY.toMinutes() + " minutes. Il confirme le changement de courriel du compte "
                + username.value() + ". Si vous n'avez rien demandé, ignorez ce message.";
        mail.sendAfterCommit(new MailMessage(to.value(), CODE_SUBJECT, body));
    }

    @Override
    public void sendEmailTakenWarning(EmailAddress to) {
        mail.sendAfterCommit(new MailMessage(to.value(), TAKEN_SUBJECT, TAKEN_BODY));
    }

    /** ACC-02 : la nouvelle adresse est masquée comme dans le journal (SEC-21). */
    @Override
    public void sendEmailChangedNotice(EmailAddress oldEmail, Username username, EmailAddress newEmail) {
        String body = "Le courriel de votre compte SEAL (identifiant : " + username.value() + ") est désormais "
                + LogValues.maskEmail(newEmail.value())
                + ". Si ce n'est pas vous, connectez-vous et changez votre mot de passe.";
        mail.sendAfterCommit(new MailMessage(oldEmail.value(), CHANGED_SUBJECT, body));
    }
}
