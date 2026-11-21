package fr.vlegall.seal.security.session;

import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * Droits portés par une session (ADR 0007, point 2) : l'état du compte, puis les facteurs obtenus, sur le modèle du MFA
 * de Spring Security 7 (AUTH-24).
 */
public final class SealAuthorities {

    /** AUTH-16 : session restreinte d'un compte dont le courriel n'est pas vérifié. */
    public static final String ACCOUNT_PENDING_EMAIL = "ACCOUNT_PENDING_EMAIL";

    /** AUTH-16 : session complète d'un compte vérifié. */
    public static final String ACCOUNT_ACTIVE = "ACCOUNT_ACTIVE";

    /** Facteur mot de passe, daté du moment où il a été obtenu (AUTH-55). */
    public static final String FACTOR_PASSWORD = FactorGrantedAuthority.PASSWORD_AUTHORITY;

    /** Facteur d'une connexion OAuth, daté de même (AUTH-30, ADR 0032, point 6). */
    public static final String FACTOR_AUTHORIZATION_CODE = FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY;

    static final String FACTOR_PREFIX = "FACTOR_";

    private SealAuthorities() {}
}
