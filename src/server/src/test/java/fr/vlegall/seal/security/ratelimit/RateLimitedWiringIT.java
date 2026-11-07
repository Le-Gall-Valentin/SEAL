package fr.vlegall.seal.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.SealIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@SealIntegrationTest
class RateLimitedWiringIT {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("SEC-10 : dans le vrai contexte, un composant dont une méthode porte @RateLimited est intercepté")
    void sec10RateLimitedBeansAreProxied() {
        Object bean =
                context.getAutowireCapableBeanFactory().initializeBean(new RateLimitedProbe(), "rateLimitedProbe");

        assertThat(AopUtils.isAopProxy(bean)).isTrue();
    }
}
