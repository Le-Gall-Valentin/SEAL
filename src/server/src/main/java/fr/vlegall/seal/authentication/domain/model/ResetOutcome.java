package fr.vlegall.seal.authentication.domain.model;

/** Issue d'une réinitialisation (AUTH-70, ADR 0022, point 6). */
public sealed interface ResetOutcome {

    /** Mot de passe remplacé, sessions du compte révoquées. */
    record Done() implements ResetOutcome {}

    /** Jeton mal formé, inconnu, expiré, déjà utilisé, ou compte qui ne peut plus le recevoir : rien n'a changé. */
    record InvalidToken() implements ResetOutcome {}

    /** Mot de passe refusé par AUTH-11 : le lien reste utilisable. */
    record WeakPassword() implements ResetOutcome {}
}
