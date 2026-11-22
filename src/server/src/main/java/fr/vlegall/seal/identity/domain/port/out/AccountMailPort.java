package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.VerificationCode;

/** Courriels du contexte {@code identity}, envoyés après la validation de la transaction (ADR 0019, point 8). */
public interface AccountMailPort {

    /** AUTH-14 : le code, et l'identifiant qui permet de se connecter avant la vérification. */
    void sendCode(EmailAddress to, Username username, VerificationCode code);

    /** AUTH-15 : avertissement au titulaire d'un compte actif, à la place du code. */
    void sendExistingAccountWarning(EmailAddress to);

    /** ACC-02 : le code qui confirme le changement de courriel du compte, envoyé à la nouvelle adresse. */
    void sendEmailChangeCode(EmailAddress to, Username username, VerificationCode code);

    /** ACC-02, ADR 0034 (point 3) : avertissement au titulaire d'une adresse déjà prise, à la place du code. */
    void sendEmailTakenWarning(EmailAddress to);

    /** ACC-02 : notification à l'ancienne adresse, qui cite la nouvelle, masquée (SEC-21). */
    void sendEmailChangedNotice(EmailAddress oldEmail, Username username, EmailAddress newEmail);
}
