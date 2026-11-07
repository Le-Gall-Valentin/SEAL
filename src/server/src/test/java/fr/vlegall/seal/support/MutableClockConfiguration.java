package fr.vlegall.seal.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Remplace l'horloge système du contexte par une {@link MutableClock} calée sur l'heure réelle. */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return MutableClock.followingSystem();
    }
}
