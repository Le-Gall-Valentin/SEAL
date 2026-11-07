package fr.vlegall.seal.common.web;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.vlegall.seal.support.SealIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

@SealIntegrationTest
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
