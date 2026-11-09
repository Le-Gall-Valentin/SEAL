package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VerificationCodeTest {

    /** SHA-256 de la chaîne ASCII « 123456 ». */
    private static final byte[] HASH_OF_123456 =
            HexFormat.of().parseHex("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92");

    @Test
    @DisplayName("AUTH-14 : le code a 6 chiffres, tirés par le port RandomDigits")
    void auth14CodeHasSixDigits() {
        assertThat(VerificationCode.generate(length -> "123456".substring(0, length))
                        .value())
                .isEqualTo("123456");
    }

    @Test
    @DisplayName("AUTH-14 : le code est stocké haché en SHA-256, jamais en clair")
    void auth14CodeIsHashedWithSha256() {
        assertThat(VerificationCode.parse("123456").orElseThrow().hash()).isEqualTo(HASH_OF_123456);
    }

    @Test
    @DisplayName("AUTH-14 : le code saisi est comparé au haché stocké")
    void auth14CodeMatchesItsHash() {
        assertThat(VerificationCode.parse("123456").orElseThrow().matches(HASH_OF_123456))
                .isTrue();
        assertThat(VerificationCode.parse("123457").orElseThrow().matches(HASH_OF_123456))
                .isFalse();
        assertThat(VerificationCode.parse("123456").orElseThrow().matches(new byte[0]))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "1234567", "12345a", " 123456", "١٢٣٤٥٦", "１２３４５６"})
    @DisplayName("AUTH-14 : seuls 6 chiffres ASCII forment un code")
    void auth14OnlySixAsciiDigitsFormACode(String raw) {
        assertThat(VerificationCode.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("SEC-21 : toString() ne révèle pas le code")
    void sec21ToStringHidesTheCode() {
        assertThat(VerificationCode.parse("123456").orElseThrow().toString()).doesNotContain("123456");
    }

    @Test
    @DisplayName("AUTH-14 : un code vit 10 minutes et accepte 5 essais")
    void auth14PolicyValues() {
        assertThat(CodePolicy.VALIDITY).isEqualTo(Duration.ofMinutes(10));
        assertThat(CodePolicy.MAX_ATTEMPTS).isEqualTo(5);
    }
}
