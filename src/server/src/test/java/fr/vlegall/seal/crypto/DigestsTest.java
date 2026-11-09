package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DigestsTest {

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
}
