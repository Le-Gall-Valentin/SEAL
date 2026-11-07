package fr.vlegall.seal.security.ratelimit;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Applique les {@link RateLimited} d'une méthode avant son exécution (ADR 0013, point 5). Échec fermé : sans requête
 * en cours, ou avec une limite qui n'est pas par IP, l'appel est refusé plutôt que laissé sans limite.
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
        for (RateLimited limit : limits) {
            if (limit.value().kind() != RateLimitKind.IP) {
                throw new IllegalStateException("@RateLimited n'accepte que des limites par IP : " + limit.value());
            }
        }
        String ip = currentIp();
        RateLimiter rateLimiter = limiter.get();
        for (RateLimited limit : limits) {
            rateLimiter.consume(limit.value(), ip);
        }
        return invocation.proceed();
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        throw new IllegalStateException("@RateLimited appelé hors d'une requête HTTP");
    }
}
