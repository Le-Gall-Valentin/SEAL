package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MSG-02 : les vecteurs de src/protocol/vectors/canonical-encoding.json, produits par
 * src/protocol/tools/gen_vectors.py, sont reproduits à l'octet près.
 */
@DisplayName("MSG-02 : vecteurs de l'encodage canonique")
class CanonicalVectorsTest {

    /** Le répertoire de travail des tests Maven est src/server. */
    private static final Path VECTORS = Path.of("..", "protocol", "vectors", "canonical-encoding.json");

    private static JsonNode vectors() throws IOException {
        return JsonMapper.builder().build().readTree(Files.readString(VECTORS));
    }

    /** Traduit un descripteur du fichier de vecteurs en appels à l'encodeur. */
    static byte[] encode(JsonNode descriptor) {
        return switch (descriptor.get("type").asString()) {
            case "u8" -> Canonical.u8(descriptor.get("value").intValue());
            case "u32" -> Canonical.u32(descriptor.get("value").longValue());
            case "u64" -> Canonical.u64(toLong(descriptor.get("value").asString()));
            case "bytes" ->
                Canonical.bytes(
                        Base64.getUrlDecoder().decode(descriptor.get("value").asString()));
            case "text" -> Canonical.text(descriptor.get("value").asString());
            case "uuid" -> Canonical.uuid(descriptor.get("value").asString());
            case "list" -> Canonical.list(CanonicalVectorsTest::encode, children(descriptor, "items"));
            case "concat" -> Canonical.concat(encodeAll(children(descriptor, "parts")));
            case "struct" ->
                Canonical.struct(descriptor.get("label").asString(), encodeAll(children(descriptor, "fields")));
            default -> throw new IllegalStateException("type inconnu : " + descriptor.get("type"));
        };
    }

    /** Un u64 hors de la plage du long de Java ne peut même pas être passé à l'encodeur : {@code ArithmeticException}. */
    private static long toLong(String decimal) {
        return new BigInteger(decimal).longValueExact();
    }

    /** Vrai si le descripteur contient un u64 que le long de Java ne peut pas représenter. */
    private static boolean exceedsLong(JsonNode descriptor) {
        if ("u64".equals(descriptor.path("type").asString(""))
                && new BigInteger(descriptor.get("value").asString()).bitLength() > 63) {
            return true;
        }
        for (String field : List.of("items", "parts", "fields")) {
            if (descriptor.has(field)) {
                for (JsonNode child : descriptor.get(field)) {
                    if (exceedsLong(child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<JsonNode> children(JsonNode descriptor, String field) {
        List<JsonNode> children = new ArrayList<>();
        descriptor.get(field).forEach(children::add);
        return children;
    }

    private static byte[][] encodeAll(List<JsonNode> descriptors) {
        return descriptors.stream().map(CanonicalVectorsTest::encode).toArray(byte[][]::new);
    }

    @TestFactory
    @DisplayName("MSG-01 : chaque cas valide donne exactement les octets attendus")
    Stream<DynamicTest> validCases() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        vectors()
                .get("valid")
                .forEach(vector -> tests.add(DynamicTest.dynamicTest(
                        vector.get("name").asString(),
                        () -> assertThat(Base64.getUrlEncoder()
                                        .withoutPadding()
                                        .encodeToString(encode(vector.get("input"))))
                                .isEqualTo(vector.get("expected").asString()))));
        assertThat(tests).isNotEmpty();
        return tests.stream();
    }

    @TestFactory
    @DisplayName("ADR 0006 : chaque cas hors domaine est rejeté")
    Stream<DynamicTest> invalidCases() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        vectors().get("invalid").forEach(vector -> {
            JsonNode input = vector.get("input");
            // ADR 0006 : l'encodeur lève CanonicalEncodingException ; un u64 au-delà d'un long n'arrive même
            // pas jusqu'à lui, la conversion en long le refuse avant.
            Class<? extends RuntimeException> expected =
                    exceedsLong(input) ? ArithmeticException.class : CanonicalEncodingException.class;
            tests.add(DynamicTest.dynamicTest(
                    vector.get("name").asString(),
                    () -> assertThatThrownBy(() -> encode(input)).isInstanceOf(expected)));
        });
        assertThat(tests).isNotEmpty();
        return tests.stream();
    }

    @Test
    @DisplayName("MSG-02 : le fichier de vecteurs annonce le format seal-vectors-v1")
    void msg02VectorsDeclareTheirFormat() throws IOException {
        assertThat(vectors().get("format").asString()).isEqualTo("seal-vectors-v1");
    }
}
