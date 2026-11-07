package fr.vlegall.seal.security.ratelimit;

import java.time.Duration;

/** Limites de débit du lot 1, recopiées de la section 15.2 du CDC (SEC-10). Chaque lot ajoute les siennes. */
public enum RateLimit {
    /** AUTH-22 : 5 échecs de connexion par 15 minutes, par identifiant saisi, existant ou non. */
    LOGIN_FAILURES_PER_LOGIN(5, Duration.ofMinutes(15)),
    /** AUTH-22 : 20 échecs de connexion par 15 minutes, par IP. */
    LOGIN_FAILURES_PER_IP(20, Duration.ofMinutes(15)),
    REGISTRATIONS_PER_IP(5, Duration.ofHours(1)),
    /** AUTH-05. */
    USERNAME_CHECKS_PER_IP(30, Duration.ofMinutes(1)),
    /** AUTH-14 : 3 renvois du code par heure. */
    EMAIL_CODE_RESENDS_PER_USER(3, Duration.ofHours(1)),
    PASSWORD_RESETS_PER_EMAIL(3, Duration.ofHours(1)),
    PASSWORD_RESETS_PER_IP(3, Duration.ofHours(1));

    private final int capacity;
    private final Duration period;

    RateLimit(int capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    /** Nombre d'actions permises par période. */
    public int capacity() {
        return capacity;
    }

    /** Période au terme de laquelle le seau se remplit entièrement. */
    public Duration period() {
        return period;
    }
}
