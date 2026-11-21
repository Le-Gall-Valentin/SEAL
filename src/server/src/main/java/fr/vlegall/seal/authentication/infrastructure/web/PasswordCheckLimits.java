package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitExceededException;
import fr.vlegall.seal.security.ratelimit.RateLimitToken;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ADR 0034, point 4 : limites d'un mot de passe vérifié dans une session (ré-authentification, mot de passe actuel
 * d'ACC-01). Ce sont celles de la connexion : 60 tentatives et 20 échecs par 15 minutes et par IP, partagés avec elle,
 * et 5 échecs par 15 minutes pour le compte. Une session volée ne sert donc pas à essayer des mots de passe sans fin.
 */
@Component
class PasswordCheckLimits {

    private final RateLimiter limiter;

    PasswordCheckLimits(RateLimiter limiter) {
        this.limiter = limiter;
    }

    /** Prend un jeton de chaque limite avant la vérification, par IP d'abord ; un refus rend ceux déjà pris. */
    void take(UserId user, String ip) {
        limiter.consumeAll(List.of(
                new RateLimitToken(RateLimit.LOGIN_ATTEMPTS_PER_IP, ip),
                new RateLimitToken(RateLimit.LOGIN_FAILURES_PER_IP, ip)));
        try {
            limiter.consume(RateLimit.PASSWORD_CHECK_FAILURES_PER_USER, user);
        } catch (RateLimitExceededException e) {
            limiter.refund(RateLimit.LOGIN_ATTEMPTS_PER_IP, ip);
            limiter.refund(RateLimit.LOGIN_FAILURES_PER_IP, ip);
            throw e;
        }
    }

    /** Une réussite rend les jetons des échecs, qui seuls comptent (ADR 0021) ; celui des tentatives reste pris. */
    void succeeded(UserId user, String ip) {
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_IP, ip);
        limiter.refund(RateLimit.PASSWORD_CHECK_FAILURES_PER_USER, user);
    }
}
