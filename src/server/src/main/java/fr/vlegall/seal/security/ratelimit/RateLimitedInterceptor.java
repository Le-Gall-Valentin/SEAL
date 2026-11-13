package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SealPrincipal;
import fr.vlegall.seal.security.web.ClientAddress;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Applique les {@link RateLimited} d'une méthode avant son exécution, dans l'ordre déclaré (ADR 0013, point 5). Échec
 * fermé : sans requête en cours pour une limite par IP, sans utilisateur authentifié pour une limite par utilisateur,
 * ou avec une limite d'une autre nature, l'appel est refusé plutôt que laissé sans limite.
 */
public final class RateLimitedInterceptor implements MethodInterceptor {

    /** Résolu à l'appel : l'intercepteur est créé avant les composants qu'il protège, pas le limiteur. */
    private final Supplier<RateLimiter> limiter;

    public RateLimitedInterceptor(Supplier<RateLimiter> limiter) {
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        Method method = target == null
                ? invocation.getMethod()
                : AopUtils.getMostSpecificMethod(invocation.getMethod(), target.getClass());
        RateLimited[] limits = method.getAnnotationsByType(RateLimited.class);
        if (limits.length == 0) {
            return invocation.proceed();
        }
        RateLimiter rateLimiter = limiter.get();
        for (RateLimited limit : limits) {
            switch (limit.value().kind()) {
                case IP -> rateLimiter.consume(limit.value(), currentIp());
                case USER -> rateLimiter.consume(limit.value(), currentUser());
                default ->
                    throw new IllegalStateException(
                            "@RateLimited n'accepte que des limites par IP ou par utilisateur : " + limit.value());
            }
        }
        return invocation.proceed();
    }

    /** Utilisateur de la session ; sans lui, échec fermé plutôt qu'une limite contournée. */
    private static UserId currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SealPrincipal principal) {
            return principal.userId();
        }
        throw new IllegalStateException("@RateLimited par utilisateur appelé sans utilisateur authentifié");
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return ClientAddress.key(attributes.getRequest().getRemoteAddr());
        }
        throw new IllegalStateException("@RateLimited appelé hors d'une requête HTTP");
    }
}
