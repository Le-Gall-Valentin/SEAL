package fr.vlegall.seal.security.ratelimit;

import java.time.Duration;

/**
 * Limites de débit de la section 15.2 du CDC (SEC-10), et celles que les ADR y ajoutent (ADR 0021, ADR 0032). Chaque
 * lot ajoute les siennes.
 */
public enum RateLimit {
    /** AUTH-22 : 5 échecs de connexion par 15 minutes, par identifiant saisi, existant ou non. */
    LOGIN_FAILURES_PER_LOGIN(5, Duration.ofMinutes(15), RateLimitKind.ENTERED),
    /** AUTH-22 : 20 échecs de connexion par 15 minutes, par IP. */
    LOGIN_FAILURES_PER_IP(20, Duration.ofMinutes(15), RateLimitKind.IP),
    /**
     * ADR 0021 : 60 tentatives de connexion par 15 minutes et par IP, réussies ou non. Sans elle, un seul compte valide
     * déclencherait autant de calculs Argon2id et de sessions qu'on voudrait.
     */
    LOGIN_ATTEMPTS_PER_IP(60, Duration.ofMinutes(15), RateLimitKind.IP),
    /**
     * ADR 0034, point 4 : 5 échecs par 15 minutes et par compte pour le mot de passe vérifié dans une session (AUTH-55,
     * ACC-01), comme ceux de la connexion. La session connaît le compte : la clé est le compte, pas la saisie.
     */
    PASSWORD_CHECK_FAILURES_PER_USER(5, Duration.ofMinutes(15), RateLimitKind.USER),
    REGISTRATIONS_PER_IP(5, Duration.ofHours(1), RateLimitKind.IP),
    /** AUTH-05. */
    USERNAME_CHECKS_PER_IP(30, Duration.ofMinutes(1), RateLimitKind.IP),
    /** AUTH-14, AUTH-17, ACC-02 : 3 envois de code par heure, renvois et changements de courriel (ADR 0034). */
    EMAIL_CODE_RESENDS_PER_USER(3, Duration.ofHours(1), RateLimitKind.USER),
    PASSWORD_RESETS_PER_EMAIL(3, Duration.ofHours(1), RateLimitKind.EMAIL),
    PASSWORD_RESETS_PER_IP(3, Duration.ofHours(1), RateLimitKind.IP),
    /**
     * ADR 0032, point 3 : 20 départs OAuth par 15 minutes et par IP. Chaque départ garde sa requête d'autorisation dans
     * une session en base : sans limite, n'importe qui remplirait la table des sessions.
     */
    OAUTH_STARTS_PER_IP(20, Duration.ofMinutes(15), RateLimitKind.IP),
    /**
     * ADR 0032, point 3 : 20 retours OAuth par 15 minutes et par IP. Chaque refus écrit une ligne du journal : sans
     * limite, n'importe qui le ferait grossir sans fin.
     */
    OAUTH_CALLBACKS_PER_IP(20, Duration.ofMinutes(15), RateLimitKind.IP);

    private final int capacity;
    private final Duration period;
    private final RateLimitKind kind;

    RateLimit(int capacity, Duration period, RateLimitKind kind) {
        this.capacity = capacity;
        this.period = period;
        this.kind = kind;
    }

    /** Nombre d'actions permises par période. */
    public int capacity() {
        return capacity;
    }

    /** Période au terme de laquelle le seau se remplit entièrement. */
    public Duration period() {
        return period;
    }

    /** Nature de la clé, qui décide de ce que le journal en garde (ADR 0013, point 2). */
    public RateLimitKind kind() {
        return kind;
    }
}
