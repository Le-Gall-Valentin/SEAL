package fr.vlegall.seal.identity.domain.model;

/**
 * Issue d'une vérification du courriel (AUTH-14). Une valeur et non une exception : un essai manqué doit être compté,
 * donc sa transaction validée (d'après NIDO).
 */
public sealed interface EmailVerification
        permits EmailVerification.Verified, EmailVerification.InvalidCode, EmailVerification.NoLiveCode {

    /** Le compte est actif. */
    record Verified() implements EmailVerification {}

    /** Code erroné ; l'essai est compté. */
    record InvalidCode() implements EmailVerification {}

    /** Aucun code vivant : expiré, consommé, remplacé ou épuisé par les 5 essais (ADR 0019, point 4). */
    record NoLiveCode() implements EmailVerification {}
}
