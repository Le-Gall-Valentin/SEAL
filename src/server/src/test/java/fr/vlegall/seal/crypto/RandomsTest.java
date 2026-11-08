package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandomsTest {

    @Test
    @DisplayName("AUTH-14 : les chiffres sont des chiffres ASCII, au nombre demandé")
    void auth14DigitsHaveTheRequestedLength() {
        assertThat(Randoms.digits(6)).hasSize(6).containsOnlyDigits().matches("[0-9]{6}");
        assertThat(Randoms.digits(1)).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-14 : les tirages varient et couvrent les dix chiffres")
    void auth14DigitsVary() {
        Set<Character> seen = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = Randoms.digits(6);
            codes.add(code);
            code.chars().forEach(c -> seen.add((char) c));
        }

        assertThat(seen).hasSize(10);
        assertThat(codes).hasSizeGreaterThan(190);
    }

    @Test
    @DisplayName("AUTH-14 : une longueur nulle ou négative est une erreur de programmation")
    void auth14RejectsNonPositiveLength() {
        assertThatThrownBy(() -> Randoms.digits(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
