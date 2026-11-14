package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AUTH-11, ADR 0024 (point 6) : la partie locale que le serveur retire d'un mot de passe ({@link PersonalWords}) est
 * celle des vecteurs de src/protocol/vectors/password-policy.json, comme côté client.
 */
@DisplayName("AUTH-11 : partie locale du courriel, d'après les vecteurs des mots de passe")
class EmailAddressVectorsTest {

    /** Le répertoire de travail des tests Maven est src/server. */
    private static final Path VECTORS = Path.of("..", "protocol", "vectors", "password-policy.json");

    static Stream<Arguments> vectors() throws IOException {
        JsonNode document = JsonMapper.builder().build().readTree(Files.readString(VECTORS));
        return document.get("cases")
                .valueStream()
                .map(vector -> Arguments.of(
                        vector.get("name").asString(),
                        vector.get("email").asString(),
                        vector.get("localPart").asString()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    @DisplayName("AUTH-11 : même partie locale que les vecteurs")
    void auth11SameLocalPartAsTheVectors(String name, String email, String localPart) {
        assertThat(EmailAddress.parse(email).orElseThrow().localPart()).as(name).isEqualTo(localPart);
    }
}
