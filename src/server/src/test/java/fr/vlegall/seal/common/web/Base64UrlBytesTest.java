package fr.vlegall.seal.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** SEC-01, PROTOCOL.md (section 1) : octets en base64url sans remplissage, sous leur seule forme canonique. */
@DisplayName("SEC-01 : octets en base64url")
class Base64UrlBytesTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private record Exactly32(
            @Base64UrlBytes(min = 32, max = 32) String value) {}

    private record UpTo4(@Base64UrlBytes(max = 4) String value) {}

    private static boolean valid(Object bean) {
        return VALIDATOR.validate(bean).isEmpty();
    }

    @Test
    @DisplayName("SEC-01 : 32 octets en base64url sans remplissage sont acceptés, et relus à l'identique")
    void sec01CanonicalValueIsAccepted() {
        byte[] bytes = new byte[32];
        bytes[31] = (byte) 0xff;
        String encoded = Base64Url.encode(bytes);

        assertThat(encoded).hasSize(43).doesNotContain("=");
        assertThat(valid(new Exactly32(encoded))).isTrue();
        assertThat(Base64Url.decode(encoded)).isEqualTo(bytes);
        assertThat(valid(new Exactly32(null))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // 31 et 33 octets.
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                // Remplissage, alphabet base64 standard, espace.
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA ",
                // Forme non canonique : les 2 derniers bits ne sont pas nuls.
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB",
                ""
            })
    @DisplayName("SEC-01 : mauvaise longueur, remplissage, autre alphabet ou forme non canonique refusés")
    void sec01AnythingElseIsRefused(String value) {
        assertThat(valid(new Exactly32(value))).isFalse();
    }

    @Test
    @DisplayName("SEC-01 : une borne haute seule accepte de 0 à max octets")
    void sec01UpperBoundOnly() {
        assertThat(valid(new UpTo4(""))).isTrue();
        assertThat(valid(new UpTo4(Base64Url.encode(new byte[4])))).isTrue();
        assertThat(valid(new UpTo4(Base64Url.encode(new byte[5])))).isFalse();
    }

    @Test
    @DisplayName("SEC-01 : décoder une valeur non canonique est une erreur de l'appelant, faute de validation")
    void sec01DecodingUnvalidatedValueFails() {
        assertThatThrownBy(() -> Base64Url.decode("AB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base64Url.decode("A=")).isInstanceOf(IllegalArgumentException.class);
    }
}
