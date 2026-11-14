package fr.vlegall.seal.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class ClientRoutesControllerTest {

    @Test
    @DisplayName("ADR 0024, point 5 : les adresses servies et celles qu'ouvre SecurityConfig sont les mêmes")
    void servedPathsAreThoseOpenedBySecurityConfig() throws NoSuchMethodException {
        GetMapping mapping =
                ClientRoutesController.class.getDeclaredMethod("client").getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactlyInAnyOrderElementsOf(ClientRoutesController.PATHS);
    }
}
