package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * KEY-10, ADR 0025 (point 12) : un chemin de la configuration est un chemin de fichiers. Dans le serveur web, le
 * chargeur de ressources du contexte est celui du contexte servlet, qui refuse {@code ../data} : la liaison ne doit
 * jamais passer par lui.
 */
@DisplayName("KEY-10 : chemins de la configuration")
class FileSystemPathsTest {

    /** Chargeur de ressources qui refuse tout, comme Tomcat refuse une ressource qui sort du contexte. */
    private static final ResourceLoader REFUSING = new ResourceLoader() {
        @Override
        public Resource getResource(String location) {
            throw new IllegalArgumentException("ressource du contexte web refusée : " + location);
        }

        @Override
        public ClassLoader getClassLoader() {
            return FileSystemPathsTest.class.getClassLoader();
        }
    };

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SealProperties.class)
    @Import(FileSystemPaths.class)
    static class Binding {}

    private final ApplicationContextRunner context = new ApplicationContextRunner(() -> {
                AnnotationConfigApplicationContext created = new AnnotationConfigApplicationContext();
                created.setResourceLoader(REFUSING);
                return created;
            })
            .withUserConfiguration(Binding.class)
            .withPropertyValues(
                    "seal.origin=https://localhost:8443",
                    "seal.security.hsts-max-age=300s",
                    "seal.session.absolute-timeout=12h",
                    "seal.mail.from=SEAL <noreply@seal.localhost>");

    @Test
    @DisplayName("KEY-10 : ../data, valeur locale par défaut, se lie en chemin de fichiers, sans ressource web")
    void key10RelativeDataDirIsAFileSystemPath() {
        context.withPropertyValues("seal.data-dir=../data")
                .run(started -> assertThat(started.getBean(SealProperties.class).dataDir())
                        .isEqualTo(Path.of("../data")));
    }

    @Test
    @DisplayName("KEY-10 : /data, valeur de l'image Docker, reste un chemin absolu")
    void key10AbsoluteDataDirStaysAbsolute() {
        context.withPropertyValues("seal.data-dir=/data")
                .run(started -> assertThat(started.getBean(SealProperties.class).dataDir())
                        .isEqualTo(Path.of("/data")));
    }
}
