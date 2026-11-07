package fr.vlegall.seal.security.ratelimit;

import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active la purge planifiée des seaux de {@link RateLimiter} et l'interception des méthodes {@link RateLimited}.
 *
 * <p>Le créateur de proxys installé aujourd'hui (celui d'AspectJ) applique tout advisor. Celui-ci est en plus déclaré
 * en rôle d'infrastructure : si AspectJ quittait le projet, Spring Boot n'installerait que le créateur de proxys
 * d'infrastructure, qui ne retient que ces advisors, et la limitation resterait branchée.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class RateLimitConfiguration {

    static Advisor rateLimitedAdvisor(RateLimitedInterceptor interceptor) {
        Pointcut single = AnnotationMatchingPointcut.forMethodAnnotation(RateLimited.class);
        Pointcut repeated = AnnotationMatchingPointcut.forMethodAnnotation(RateLimitedList.class);
        return new DefaultPointcutAdvisor(new ComposablePointcut(single).union(repeated), interceptor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor rateLimitedMethodsAdvisor(ObjectProvider<RateLimiter> limiter) {
        return rateLimitedAdvisor(new RateLimitedInterceptor(limiter::getObject));
    }
}
