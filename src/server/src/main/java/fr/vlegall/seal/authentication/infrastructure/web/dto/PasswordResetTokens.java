package fr.vlegall.seal.authentication.infrastructure.web.dto;

/** Forme d'un jeton dans une requête : 43 caractères base64url, refusée en 400 avant toute requête SQL (SEC-01). */
final class PasswordResetTokens {

    static final String PATTERN = "^[A-Za-z0-9_-]{43}$";

    private PasswordResetTokens() {}
}
