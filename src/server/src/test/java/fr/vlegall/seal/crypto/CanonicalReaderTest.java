package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-33, MSG-01 : relecture de l'encodage canonique (PROTOCOL.md, section 2), symétrique de {@link Canonical}. */
class CanonicalReaderTest {

    private static final UUID ID = UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c");
    private static final String LABEL = "SEAL-test-v1";

    private static byte[] sample() {
        return Canonical.struct(
                LABEL,
                Canonical.bytes(new byte[] {1, 2}),
                Canonical.uuid(ID),
                Canonical.text("éa"),
                Canonical.u64(1_794_009_600_000L),
                Canonical.u8(3));
    }

    /** Lit l'exemple entier, fin comprise. */
    private static void readSample(byte[] encoded) {
        CanonicalReader reader = CanonicalReader.of(encoded, LABEL);
        reader.bytes();
        reader.uuid();
        reader.text();
        reader.u64();
        reader.u8();
        reader.end();
    }

    @Test
    @DisplayName("MSG-01 : chaque primitive écrite par Canonical se relit à l'identique, puis la fin est exigée")
    void msg01ReadsWhatCanonicalWrites() {
        CanonicalReader reader = CanonicalReader.of(sample(), LABEL);

        assertThat(reader.bytes()).containsExactly(1, 2);
        assertThat(reader.uuid()).isEqualTo(ID);
        assertThat(reader.text()).isEqualTo("éa");
        assertThat(reader.u64()).isEqualTo(1_794_009_600_000L);
        assertThat(reader.u8()).isEqualTo(3);
        reader.end();
    }

    @Test
    @DisplayName("MSG-01 : une autre étiquette est refusée")
    void msg01OtherLabelIsRefused() {
        assertThatThrownBy(() -> CanonicalReader.of(sample(), "SEAL-autre-v1"))
                .isInstanceOf(CanonicalEncodingException.class);
    }

    @Test
    @DisplayName("MSG-01 : chaque troncature et un octet en trop sont refusés")
    void msg01TruncationAndTrailingBytesAreRefused() {
        byte[] encoded = sample();
        for (int length = 0; length < encoded.length; length++) {
            byte[] truncated = Arrays.copyOf(encoded, length);
            assertThatThrownBy(() -> readSample(truncated))
                    .as("troncature à %d octets", length)
                    .isInstanceOf(CanonicalEncodingException.class);
        }
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThatThrownBy(() -> readSample(trailing)).isInstanceOf(CanonicalEncodingException.class);
    }

    @Test
    @DisplayName("MSG-01 : un u64 au-delà de 2^63 - 1 et un texte UTF-8 invalide sont refusés")
    void msg01OutOfRangeValuesAreRefused() {
        byte[] huge = Canonical.concat(Canonical.text(LABEL), new byte[] {(byte) 0x80, 0, 0, 0, 0, 0, 0, 0});
        assertThatThrownBy(() -> CanonicalReader.of(huge, LABEL).u64()).isInstanceOf(CanonicalEncodingException.class);

        byte[] invalidUtf8 = Canonical.concat(Canonical.text(LABEL), Canonical.bytes(new byte[] {(byte) 0xC3}));
        assertThatThrownBy(() -> CanonicalReader.of(invalidUtf8, LABEL).text())
                .isInstanceOf(CanonicalEncodingException.class);
    }

    @Test
    @DisplayName("MSG-01, ADR 0006 : un texte UTF-8 valide qui n'est pas en NFC est refusé")
    void msg01NonNfcTextIsRefused() {
        byte[] decomposed =
                Canonical.concat(Canonical.text(LABEL), Canonical.bytes("e\u0301".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> CanonicalReader.of(decomposed, LABEL).text())
                .isInstanceOf(CanonicalEncodingException.class);
    }
}
