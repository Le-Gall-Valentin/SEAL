package fr.vlegall.seal.identity.domain.model;

/**
 * Issue d'une confirmation de changement de courriel (ACC-02). Une valeur et non une exception : un essai manqué doit
 * être compté, donc sa transaction validée, comme pour {@link EmailVerification}.
 */
public sealed interface EmailChangeConfirmation {

    /** Le compte porte la nouvelle adresse. */
    record Changed() implements EmailChangeConfirmation {}

    /** Code erroné ; l'essai est compté. */
    record InvalidCode() implements EmailChangeConfirmation {}

    /**
     * Aucun code vivant : expiré, consommé, remplacé, épuisé par les 5 essais, ou consommé parce qu'un autre compte a
     * activé l'adresse entre-temps (ADR 0034, point 3).
     */
    record NoLiveCode() implements EmailChangeConfirmation {}
}
