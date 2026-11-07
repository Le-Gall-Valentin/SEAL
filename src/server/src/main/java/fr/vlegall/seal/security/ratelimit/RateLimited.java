package fr.vlegall.seal.security.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limite de débit déclarative, posée sur une méthode de contrôleur (ADR 0013, point 5, d'après NIDO) : la limite est
 * consommée avant l'appel, hors de toute transaction, avec l'IP de la requête pour clé. Seules les limites de nature
 * {@link RateLimitKind#IP} s'y prêtent ; les limites qui ne comptent que des échecs restent des appels explicites à
 * {@link RateLimiter#consume} et {@link RateLimiter#refund}. Répétable pour cumuler plusieurs limites.
 */
@Repeatable(RateLimitedList.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    RateLimit value();
}
