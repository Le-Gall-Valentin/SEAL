package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import fr.vlegall.seal.crypto.Digests;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResetTokenTest {

    private static byte[] bytes(int first) {
        byte[] bytes = new byte[ResetToken.BYTES];
        Arrays.fill(bytes, (byte) 7);
        bytes[0] = (byte) first;
        return bytes;
    }

    @Test
    @DisplayName("AUTH-70 : un jeton de 256 bits s'écrit en 43 caractères base64url, sans remplissage")
    void auth70TokenIs43Base64UrlCharacters() {
        ResetToken token = ResetToken.of(bytes(0xFB));

        assertThat(token.value()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(token.value())
                .isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(0xFB)));
    }

    @Test
    @DisplayName("AUTH-70 : seul le SHA-256 des 32 octets est conservé, jamais le jeton (ADR 0022)")
    void auth70HashIsTheSha256OfTheRawBytes() {
        assertThat(ResetToken.of(bytes(1)).hash()).isEqualTo(Digests.sha256(bytes(1)));
        assertThat(ResetToken.of(bytes(1)).hash())
                .isNotEqualTo(ResetToken.of(bytes(2)).hash());
    }

    @Test
    @DisplayName("AUTH-70 : un jeton relu par parse redonne le même jeton et le même haché")
    void auth70ParsedTokenRoundTrips() {
        ResetToken token = ResetToken.of(bytes(42));

        assertThat(ResetToken.parse(token.value())).hasValueSatisfying(parsed -> {
            assertThat(parsed.value()).isEqualTo(token.value());
            assertThat(parsed.hash()).isEqualTo(token.hash());
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA ",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAé"
            })
    @DisplayName("AUTH-70 : une saisie qui n'est pas exactement un jeton canonique est refusée")
    void auth70MalformedTokenIsRejected(String input) {
        assertThat(ResetToken.parse(input)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : un jeton se construit sur exactement 32 octets")
    void auth70TokenNeedsExactly32Bytes() {
        assertThatIllegalArgumentException().isThrownBy(() -> ResetToken.of(new byte[31]));
        assertThatIllegalArgumentException().isThrownBy(() -> ResetToken.of(new byte[33]));
    }

    @Test
    @DisplayName("SEC-21 : toString ne révèle jamais le jeton")
    void sec21ToStringHidesTheToken() {
        ResetToken token = ResetToken.of(bytes(9));

        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    @DisplayName("AUTH-70 : un lien vaut 30 minutes")
    void auth70LinkLastsThirtyMinutes() {
        assertThat(ResetToken.VALIDITY).isEqualTo(Duration.ofMinutes(30));
    }
}
