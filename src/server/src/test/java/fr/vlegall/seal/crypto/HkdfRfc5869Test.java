package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.ProtocolVectors;
import java.util.HexFormat;
import java.util.stream.Stream;
import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/**
 * QA-01 : HKDF-SHA256 de la JCA (KDF de la JDK 25) sur les vecteurs officiels de la RFC 5869 (annexe A, cas 1 à 3).
 * Le client s'en sert pour la KEK du coffre (CDC 6.4) ; le serveur, pas encore.
 */
@DisplayName("QA-01 : HKDF-SHA256")
class HkdfRfc5869Test {

    private static final HexFormat HEX = HexFormat.of();

    private static byte[] hex(JsonNode test, String field) {
        return HEX.parseHex(test.get(field).asString());
    }

    @TestFactory
    @DisplayName("QA-01 : chaque cas de la RFC 5869 (annexe A) est reproduit à l'octet près")
    Stream<DynamicTest> qa01Rfc5869CasesAreReproduced() {
        return ProtocolVectors.hkdfRfc5869()
                .cases("tests")
                .map(test -> DynamicTest.dynamicTest(test.get("name").asString(), () -> {
                    HKDFParameterSpec.Builder extract =
                            HKDFParameterSpec.ofExtract().addIKM(hex(test, "ikm"));
                    byte[] salt = hex(test, "salt");
                    // RFC 5869, 2.2 : un sel absent vaut HashLen octets nuls, ce que fait la JCA sans addSalt.
                    if (salt.length > 0) {
                        extract.addSalt(salt);
                    }
                    byte[] okm = KDF.getInstance("HKDF-SHA256")
                            .deriveData(extract.thenExpand(
                                    hex(test, "info"), test.get("length").asInt()));

                    assertThat(okm).isEqualTo(hex(test, "okm"));
                }));
    }
}
