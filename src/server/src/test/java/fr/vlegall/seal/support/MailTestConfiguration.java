package fr.vlegall.seal.support;

import fr.vlegall.seal.common.mail.MailExecutor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Remplace le {@code JavaMailSender} de Spring Boot par {@link RecordingMailSender}, et envoie les courriels sur le fil
 * de la requête : quand MockMvc rend la réponse, chaque courriel décidé est déjà reçu, et une absence est définitive.
 * L'envoi hors de la requête est vérifié à part, par {@code BoundedMailExecutorTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MailTestConfiguration {

    @Bean
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }

    @Bean
    @Primary
    MailExecutor callerThreadMailExecutor() {
        return Runnable::run;
    }
}
