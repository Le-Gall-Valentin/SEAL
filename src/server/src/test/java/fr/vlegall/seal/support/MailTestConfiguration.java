package fr.vlegall.seal.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Remplace le {@code JavaMailSender} de Spring Boot par {@link RecordingMailSender}. Les courriels partent sur le fil de
 * la requête, comme toute tâche d'arrière-plan ({@link BackgroundTestConfiguration}) : quand MockMvc rend la réponse,
 * chaque courriel décidé est déjà reçu, et une absence est définitive.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MailTestConfiguration {

    @Bean
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
