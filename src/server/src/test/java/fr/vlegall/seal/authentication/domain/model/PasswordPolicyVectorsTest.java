package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AUTH-11, ADR 0024 (point 6) : les vecteurs de src/protocol/vectors/password-policy.json, produits par
 * src/protocol/tools/gen_vectors.py, donnent le même verdict au serveur et au client.
 */
@DisplayName("AUTH-11 : vecteurs de la politique des mots de passe")
class PasswordPolicyVectorsTest {

    /** Le répertoire de travail des tests Maven est src/server. */
    private static final Path PROTOCOL = Path.of("..", "protocol");

    private static List<JsonNode> cases() throws IOException {
        JsonNode document = JsonMapper.builder()
                .build()
                .readTree(Files.readString(PROTOCOL.resolve("vectors").resolve("password-policy.json")));
        return document.get("cases").valueStream().toList();
    }

    /** La liste de blocage du dépôt, repliée comme le fait ClasspathBlocklist. */
    private static Blocklist blocklist() throws IOException {
        Set<String> entries;
        try (Stream<String> lines = Files.lines(PROTOCOL.resolve("password-blocklist.txt"), StandardCharsets.UTF_8)) {
            entries = lines.map(PasswordPolicy::fold).collect(Collectors.toUnmodifiableSet());
        }
        return entries::contains;
    }

    static Stream<Arguments> vectors() throws IOException {
        Blocklist blocklist = blocklist();
        return cases().stream().map(vector -> Arguments.of(vector.get("name").asString(), vector, blocklist));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    @DisplayName("AUTH-11 : même verdict que les vecteurs")
    void auth11SameVerdictAsTheVectors(String name, JsonNode vector, Blocklist blocklist) {
        Password password = Password.fromInput(vector.get("password").asString());
        List<String> personalWords = List.of(
                vector.get("username").asString(), vector.get("localPart").asString());

        assertThat(PasswordPolicy.accepts(password, personalWords, blocklist))
                .as(name)
                .isEqualTo(vector.get("accepted").asBoolean());
    }

    @Test
    @DisplayName("AUTH-11 : les vecteurs couvrent des mots de passe acceptés et refusés")
    void auth11VectorsCoverBothVerdicts() throws IOException {
        assertThat(cases())
                .extracting(vector -> vector.get("accepted").asBoolean())
                .contains(true, false);
    }
}
