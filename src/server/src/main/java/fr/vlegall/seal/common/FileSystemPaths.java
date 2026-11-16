package fr.vlegall.seal.common;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * KEY-10, ADR 0025 (point 12) : un chemin de la configuration ({@code seal.data-dir}) est un chemin de fichiers,
 * converti par {@link Path#of}. Sans ce convertisseur, Spring le lit comme une ressource du chargeur du contexte, qui
 * est, dans le serveur web, le contexte servlet : {@code ../data} y est refusé, et le mode développement ne démarrait
 * pas.
 */
@Configuration(proxyBeanMethods = false)
public class FileSystemPaths {

    /** Statique : la liaison des propriétés en a besoin avant que les autres beans existent. */
    @Bean
    @ConfigurationPropertiesBinding
    static Converter<String, Path> fileSystemPath() {
        return new ToPath();
    }

    /** Une classe nommée, pas un lambda : Spring lit ses types génériques pour savoir quand l'appliquer. */
    static final class ToPath implements Converter<String, Path> {
        @Override
        public Path convert(String source) {
            return Path.of(source);
        }
    }
}
