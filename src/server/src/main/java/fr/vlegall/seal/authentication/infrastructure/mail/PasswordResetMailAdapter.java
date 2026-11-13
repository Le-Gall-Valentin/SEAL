package fr.vlegall.seal.authentication.infrastructure.mail;

import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetMailPort;
import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.common.mail.MailDispatcher;
import fr.vlegall.seal.common.mail.MailMessage;
import org.springframework.stereotype.Component;

/**
 * Courriel du lien de réinitialisation, en français et en texte brut (ADR 0019 point 8, ADR 0022). Le jeton est dans le
 * fragment du lien, que le navigateur n'envoie jamais au serveur (ADR 0022, point 2).
 */
@Component
public class PasswordResetMailAdapter implements PasswordResetMailPort {

    static final String SUBJECT = "Réinitialisation de votre mot de passe SEAL";

    /** ADR 0022, point 2 : chemin de l'écran Nouveau-Mot-de-passe, servi par le client. */
    static final String PATH = "/mot-de-passe/nouveau#jeton=";

    private final MailDispatcher mail;
    private final SealProperties seal;

    public PasswordResetMailAdapter(MailDispatcher mail, SealProperties seal) {
        this.mail = mail;
        this.seal = seal;
    }

    @Override
    public void sendLink(String email, String username, ResetToken token) {
        String link = seal.origin() + PATH + token.value();
        // Lien seul sur sa ligne : aucun client de messagerie n'y colle la ponctuation (ADR 0022, mise à jour).
        String body = "Pour choisir un nouveau mot de passe pour @" + username + ", ouvrez ce lien :\n\n" + link
                + "\n\nIl est valable " + ResetToken.VALIDITY.toMinutes() + " minutes et ne sert qu'une fois."
                + " Vos clés ne changent pas. Si vous n'avez rien demandé, ignorez ce message : votre mot de passe"
                + " reste inchangé.";
        mail.sendAfterCommit(new MailMessage(email, SUBJECT, body));
    }
}
