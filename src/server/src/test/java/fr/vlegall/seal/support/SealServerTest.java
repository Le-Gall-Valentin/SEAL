package fr.vlegall.seal.support;

import fr.vlegall.seal.TestcontainersConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test d'intégration qui a besoin du vrai serveur, sans TLS (profil test) : la WebSocket (TRN-10) n'existe pas avec
 * MockMvc. Les comptes s'ouvrent par MockMvc, sur la même base de sessions ; les tests de la WebSocket partagent ce
 * contexte.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    MutableClockConfiguration.class,
    MailTestConfiguration.class,
    BackgroundTestConfiguration.class,
    PasswordHashingProbeConfiguration.class
})
public @interface SealServerTest {}
