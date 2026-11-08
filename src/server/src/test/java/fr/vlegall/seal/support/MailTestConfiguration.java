package fr.vlegall.seal.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Remplace le {@code JavaMailSender} de Spring Boot par {@link RecordingMailSender}. */
@TestConfiguration(proxyBeanMethods = false)
public class MailTestConfiguration {

    @Bean
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
