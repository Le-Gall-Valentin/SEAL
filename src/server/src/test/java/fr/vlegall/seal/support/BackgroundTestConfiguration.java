package fr.vlegall.seal.support;

import fr.vlegall.seal.common.BackgroundTasks;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Exécute les tâches d'arrière-plan sur le fil de la requête : quand MockMvc rend la réponse, la tâche est faite, et
 * une absence d'effet est définitive. L'exécution hors de la requête est vérifiée à part, par
 * {@code BoundedBackgroundTasksTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BackgroundTestConfiguration {

    @Bean
    @Primary
    BackgroundTasks callerThreadBackgroundTasks() {
        return Runnable::run;
    }
}
