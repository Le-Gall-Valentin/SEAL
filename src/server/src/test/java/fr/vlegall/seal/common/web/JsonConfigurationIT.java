package fr.vlegall.seal.common.web;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.vlegall.seal.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JsonConfigurationIT {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("Section 14 : un champ JSON inconnu est refusé")
    void unknownFieldsAreRefused() {
        assertThatExceptionOfType(UnrecognizedPropertyException.class)
                .isThrownBy(() -> jsonMapper.readValue("{\"name\":\"a\",\"admin\":true}", Probe.class));
    }

    record Probe(String name) {}
}
