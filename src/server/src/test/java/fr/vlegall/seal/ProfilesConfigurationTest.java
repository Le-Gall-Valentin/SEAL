package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Vérifie les fichiers de configuration par profil. Les tests d'intégration tournent sans TLS (profil test) : ce
 * qu'ils ne peuvent pas démarrer est vérifié ici, et le job compose de la CI vérifie le TLS réel.
 */
class ProfilesConfigurationTest {

    private static Binder binderFor(String... files) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        // Le dernier fichier l'emporte, comme un profil sur la configuration commune.
        for (String file : files) {
            for (PropertySource<?> source : loader.load(file, new ClassPathResource(file))) {
                environment.getPropertySources().addFirst(source);
            }
        }
        return new Binder(ConfigurationPropertySources.get(environment));
    }

    @Test
    @DisplayName("TRN-01 : le profil tls n'accepte que TLS 1.3, avec le bundle PEM")
    void trn01TlsProfileOnlyAllowsTls13() throws IOException {
        Binder binder = binderFor("application.yaml", "application-tls.yaml");

        assertThat(binder.bind("server.ssl.bundle", String.class).get()).isEqualTo("seal-tls");
        assertThat(binder.bind("spring.ssl.bundle.pem.seal-tls.options.enabled-protocols", String[].class)
                        .get())
                .containsExactly("TLSv1.3");
    }

    @Test
    @DisplayName("TRN-01 : les profils demo et prod activent TLS, demo s'applique par défaut")
    void trn01DemoAndProdBothEnableTls() throws IOException {
        Binder binder = binderFor("application.yaml");

        assertThat(binder.bind("spring.profiles.default", String.class).get()).isEqualTo("demo");
        assertThat(binder.bind("spring.profiles.group.demo", String[].class).get())
                .containsExactly("tls");
        assertThat(binder.bind("spring.profiles.group.prod", String[].class).get())
                .containsExactly("tls");
    }

    @Test
    @DisplayName("TRN-03 : HSTS de 300 secondes en demo et d'un an en prod")
    void trn03HstsDurationDependsOnTheProfile() throws IOException {
        Duration demo = binderFor("application.yaml")
                .bind("seal.security.hsts-max-age", Duration.class)
                .get();
        Duration prod = binderFor("application.yaml", "application-prod.yaml")
                .bind("seal.security.hsts-max-age", Duration.class)
                .get();

        assertThat(demo).hasSeconds(300);
        assertThat(prod).hasSeconds(31_536_000);
    }

    @Test
    @DisplayName("DATA-01 : Hibernate valide le schéma sans jamais le modifier")
    void data01HibernateOnlyValidatesTheSchema() throws IOException {
        Binder binder = binderFor("application.yaml");

        assertThat(binder.bind("spring.jpa.hibernate.ddl-auto", String.class).get())
                .isEqualTo("validate");
        assertThat(binder.bind("management.endpoints.web.exposure.include", String[].class)
                        .get())
                .isEqualTo(List.of("health").toArray(String[]::new));
    }
}
