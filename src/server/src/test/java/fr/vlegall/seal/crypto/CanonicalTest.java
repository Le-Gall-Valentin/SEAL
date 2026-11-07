package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cas propres à l'API Java, en complément des vecteurs partagés (CanonicalVectorsTest). */
class CanonicalTest {

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    @Test
    @DisplayName("MSG-01 : l'étiquette ouvre la structure, puis les champs dans l'ordre donné")
    void msg01LabelFirstThenFieldsInOrder() {
        byte[] encoded = Canonical.struct("L", Canonical.u8(1), Canonical.u8(2));

        assertThat(hex(encoded)).isEqualTo("000000014c" + "01" + "02");
        assertThat(encoded).isNotEqualTo(Canonical.struct("L", Canonical.u8(2), Canonical.u8(1)));
    }

    @Test
    @DisplayName("MSG-01 : un champ absent s'encode comme une chaîne d'octets vide")
    void msg01AbsentFieldIsAnEmptyByteString() {
        assertThat(hex(Canonical.bytes(new byte[0]))).isEqualTo("00000000");
        assertThat(Canonical.text("")).isEqualTo(Canonical.bytes(new byte[0]));
    }

    @Test
    @DisplayName("ADR 0006 : un java.util.UUID donne les mêmes octets que sa forme textuelle")
    void uuidObjectMatchesItsTextualForm() {
        UUID id = UUID.fromString("0192f7a2-3c4d-7e5f-8a6b-1c2d3e4f5a6b");

        assertThat(Canonical.uuid(id)).isEqualTo(Canonical.uuid("0192F7A2-3C4D-7E5F-8A6B-1C2D3E4F5A6B"));
        assertThat(hex(Canonical.uuid(id))).isEqualTo("0192f7a23c4d7e5f8a6b1c2d3e4f5a6b");
    }

    @Test
    @DisplayName("MSG-01 : bytes copie son entrée, la modifier ensuite ne change pas l'encodage")
    void bytesCopiesItsInput() {
        byte[] input = {1, 2, 3};
        byte[] encoded = Canonical.bytes(input);

        input[0] = 9;

        assertThat(hex(encoded)).isEqualTo("00000003" + "010203");
    }

    @Test
    @DisplayName("MSG-01 : list encode le nombre d'éléments puis chaque élément avec l'encodeur donné")
    void listEncodesCountThenEachElement() {
        byte[] encoded = Canonical.list(Canonical::u64, List.of(1L, 2L));

        assertThat(hex(encoded)).isEqualTo("00000002" + "0000000000000001" + "0000000000000002");
    }

    @Test
    @DisplayName("ADR 0006 : u64 accepte 2^63-1 et rejette tout long négatif")
    void u64AcceptsLongMaxAndRejectsNegatives() {
        assertThat(hex(Canonical.u64(Long.MAX_VALUE))).isEqualTo("7fffffffffffffff");
        assertThatThrownBy(() -> Canonical.u64(Long.MIN_VALUE)).isInstanceOf(CanonicalEncodingException.class);
    }

    @Test
    @DisplayName("ADR 0006 : une valeur nulle est refusée, jamais encodée comme vide")
    void nullIsRefused() {
        assertThatNullPointerException().isThrownBy(() -> Canonical.bytes(null));
        assertThatNullPointerException().isThrownBy(() -> Canonical.text(null));
        assertThatNullPointerException().isThrownBy(() -> Canonical.uuid((String) null));
        assertThatNullPointerException().isThrownBy(() -> Canonical.uuid((UUID) null));
        assertThatNullPointerException().isThrownBy(() -> Canonical.struct(null));
        assertThatNullPointerException().isThrownBy(() -> Canonical.list(Canonical::u64, null));
        assertThatNullPointerException().isThrownBy(() -> Canonical.concat((byte[]) null));
    }
}
