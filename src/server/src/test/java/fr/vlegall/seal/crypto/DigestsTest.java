package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DigestsTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    @DisplayName("AUTH-14 : SHA-256 suit le vecteur de FIPS 180-2 pour « abc »")
    void sha256MatchesTheFipsVector() {
        byte[] digest = Digests.sha256("abc".getBytes(StandardCharsets.US_ASCII));

        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("AUTH-14 : l'empreinte d'une entrée vide est celle de la norme")
    void sha256OfEmptyInput() {
        assertThat(HexFormat.of().formatHex(Digests.sha256(new byte[0])))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("AUTH-14 : deux empreintes égales se comparent en temps constant (A04)")
    void equalComparesDigests() {
        byte[] digest = Digests.sha256(new byte[] {1});

        assertThat(Digests.equal(digest, Digests.sha256(new byte[] {1}))).isTrue();
        assertThat(Digests.equal(digest, Digests.sha256(new byte[] {2}))).isFalse();
        assertThat(Digests.equal(digest, new byte[0])).isFalse();
    }

    @ParameterizedTest(name = "RFC 4231, cas {0}")
    @CsvSource({
        "1, 0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b, 4869205468657265,"
                + " b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
        "2, 4a656665, 7768617420646f2079612077616e7420666f72206e6f7468696e673f,"
                + " 5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
    })
    @DisplayName("AUTH-13, ADR 0026 : HMAC-SHA256 suit les vecteurs de la RFC 4231")
    void auth13HmacSha256MatchesTheRfc(int number, String key, String message, String expected) {
        assertThat(HexFormat.of().formatHex(Digests.hmacSha256(HEX.parseHex(key), HEX.parseHex(message))))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 : une clé plus longue qu'un bloc est d'abord hachée (RFC 4231, cas 6)")
    void auth13HmacSha256HashesALongKey() {
        byte[] key = new byte[131];
        Arrays.fill(key, (byte) 0xaa);
        byte[] message = "Test Using Larger Than Block-Size Key - Hash Key First".getBytes(StandardCharsets.US_ASCII);

        assertThat(HexFormat.of().formatHex(Digests.hmacSha256(key, message)))
                .isEqualTo("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54");
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 : une clé vide est refusée, jamais un HMAC sans secret")
    void auth13EmptyKeyIsRefused() {
        assertThatThrownBy(() -> Digests.hmacSha256(new byte[0], new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
