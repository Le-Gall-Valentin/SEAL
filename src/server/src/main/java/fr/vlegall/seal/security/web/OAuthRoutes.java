package fr.vlegall.seal.security.web;

/**
 * AUTH-30, AUTH-35 : les adresses OAuth de Spring Security, partagées par les règles d'accès, les limites de débit et
 * les gestionnaires du contexte {@code authentication}. Chacune est suivie du {@code registrationId}.
 */
public final class OAuthRoutes {

    /** Départ : {@code /oauth2/authorization/{registrationId}}. */
    public static final String AUTHORIZATION_BASE = "/oauth2/authorization";

    /** Retour, adresse de redirection déclarée chez le fournisseur : {@code /login/oauth2/code/{registrationId}}. */
    public static final String CALLBACK_BASE = "/login/oauth2/code";

    private OAuthRoutes() {}
}
