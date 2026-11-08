package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "valentin.le.gall", "a_b", "a1.b2_c3", "abcdefghijklmnopqrst"})
    @DisplayName("AUTH-01 : un identifiant au format imposé est accepté tel quel")
    void auth01AcceptsAWellFormedUsername(String raw) {
        assertThat(Username.parse(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "ab",
                "abcdefghijklmnopqrstu",
                "AB",
                "Abc",
                "a..b",
                "a._b",
                "_ab",
                "ab_",
                ".ab",
                "ab.",
                "a-b",
                "a b",
                "émile",
                "abc\n"
            })
    @DisplayName("AUTH-01 : tout autre identifiant est refusé avec USERNAME_INVALID")
    void auth01RejectsAnyOtherUsername(String raw) {
        assertThatThrownBy(() -> Username.parse(raw))
                .isInstanceOf(IdentityException.UsernameInvalid.class)
                .extracting(e -> ((IdentityException) e).code())
                .isEqualTo(ErrorCode.USERNAME_INVALID);
    }

    @Test
    @DisplayName("AUTH-01 : un identifiant invalide n'est pas recopié dans le message de l'exception")
    void auth01InvalidUsernameIsNotEchoed() {
        assertThatThrownBy(() -> Username.parse("A\u202Eevil")).hasMessageNotContaining("evil");
    }

    @Test
    @DisplayName("AUTH-01 : la vérification accepte exactement ce qu'accepte l'expression du CDC")
    void auth01MatchesTheCdcExpression() {
        Pattern cdc = Pattern.compile("^(?=.{3,20}$)[a-z0-9]+(?:[._][a-z0-9]+)*$");
        // Surtout des alphanumériques, quelques séparateurs, rarement un caractère interdit.
        String alphabet = "abcdefgh0123456789ab09.._A-";
        Random random = new Random(14);
        int accepted = 0;
        for (int i = 0; i < 20_000; i++) {
            StringBuilder candidate = new StringBuilder();
            int length = random.nextInt(23);
            for (int j = 0; j < length; j++) {
                candidate.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            boolean expected = cdc.matcher(candidate).matches();
            boolean actual = isAccepted(candidate.toString());
            assertThat(actual).as(candidate.toString()).isEqualTo(expected);
            accepted += expected ? 1 : 0;
        }
        assertThat(accepted)
                .as("assez de cas acceptés pour que la comparaison compte")
                .isGreaterThan(1_000);
    }

    private static boolean isAccepted(String candidate) {
        try {
            Username.parse(candidate);
            return true;
        } catch (IdentityException.UsernameInvalid e) {
            return false;
        }
    }
}
