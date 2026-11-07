package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComposeImageConsistencyTest {

    @Test
    @DisplayName("QA-05 : les tests d'intégration utilisent la même image PostgreSQL que docker compose")
    void qa05TestcontainersUsesTheComposeImage() throws IOException {
        String compose = Files.readString(Path.of("..", "compose.yaml"));

        assertThat(compose).contains("image: " + TestcontainersConfiguration.POSTGRES_IMAGE);
    }
}
