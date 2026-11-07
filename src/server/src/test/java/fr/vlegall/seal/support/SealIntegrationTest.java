package fr.vlegall.seal.support;

import fr.vlegall.seal.TestcontainersConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test d'intégration de SEAL : contexte complet, MockMvc, PostgreSQL jetable et horloge réglable. Les tests
 * d'intégration qui passent par MockMvc partagent cette configuration, donc un seul contexte Spring et un seul
 * conteneur ; seuls ceux qui ont besoin du vrai serveur (dispatch d'erreur de Tomcat, comme {@code ErrorDispatchIT})
 * démarrent un second contexte.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
public @interface SealIntegrationTest {}
