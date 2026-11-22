package fr.vlegall.seal.authentication.domain.model;

/** Issue d'un changement de mot de passe depuis les paramètres (ACC-01, ADR 0034). */
public sealed interface PasswordChangeOutcome {

    /**
     * Mot de passe remplacé.
     *
     * @param version version du mot de passe désormais en base, à graver dans la session courante (ADR 0023)
     */
    record Changed(long version) implements PasswordChangeOutcome {}

    /**
     * Mot de passe actuel faux, compte sans mot de passe ou qui n'est plus actif, ou mot de passe remplacé entre-temps :
     * rien n'a changé.
     */
    record InvalidCurrentPassword() implements PasswordChangeOutcome {}

    /** Nouveau mot de passe refusé par AUTH-11 : rien n'a changé. */
    record WeakPassword() implements PasswordChangeOutcome {}
}
