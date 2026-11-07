package fr.vlegall.seal.security.ratelimit;

/** Classe de test dont les méthodes portent {@link RateLimited}, comme le feront les contrôleurs. */
public class RateLimitedProbe {

    @RateLimited(RateLimit.REGISTRATIONS_PER_IP)
    public String register() {
        return "inscrit";
    }

    @RateLimited(RateLimit.REGISTRATIONS_PER_IP)
    @RateLimited(RateLimit.USERNAME_CHECKS_PER_IP)
    public String registerAndCheck() {
        return "deux limites";
    }

    @RateLimited(RateLimit.EMAIL_CODE_RESENDS_PER_USER)
    public String resend() {
        return "renvoyé";
    }

    public String free() {
        return "libre";
    }
}
