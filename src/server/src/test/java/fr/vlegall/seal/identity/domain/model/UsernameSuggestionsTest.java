package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsernameSuggestionsTest {

    private static EmailAddress email(String raw) {
        return EmailAddress.parse(raw).orElseThrow();
    }

    @Test
    @DisplayName("AUTH-04 : l'exemple du CDC donne valentin.le.gall")
    void auth04CdcExample() {
        assertThat(UsernameSuggestions.baseFromEmail(email("Valéntin.Le-Gall+uqac@exemple.ca")))
                .isEqualTo("valentin.le.gall");
    }

    @Test
    @DisplayName("AUTH-04 : une base de moins de 3 caractères devient user")
    void auth04ShortBaseBecomesUser() {
        assertThat(UsernameSuggestions.baseFromEmail(email("é@x.ca"))).isEqualTo("user");
    }

    @ParameterizedTest
    @CsvSource({
        "Valentin Dupont, valentin.dupont",
        "a--b__c, a.b.c",
        "..abc.., abc",
        "張偉, user",
        "abcdefghijklmnopqrstuvwxyz, abcdefghijklmnopqrst",
        "abcdefghijklmnopqrs.tuvw, abcdefghijklmnopqrs",
        "ＡＢＣ, abc"
    })
    @DisplayName("AUTH-04 : étapes 2 à 4, normalisation, séparateurs fusionnés, troncature sans séparateur final")
    void auth04NormalisesAnyText(String text, String expected) {
        assertThat(UsernameSuggestions.baseFrom(text)).isEqualTo(expected);
    }

    @Test
    @DisplayName("AUTH-04 : la base est toujours un identifiant valide")
    void auth04BaseIsAlwaysValid() {
        for (String text : List.of("x", "_", "a.b", "Émile.Zola+x", "1234567890123456789012")) {
            Username.parse(UsernameSuggestions.baseFrom(text));
        }
    }

    @Test
    @DisplayName("AUTH-04 : étape 5, suffixes 2 à 99 dans l'ordre")
    void auth04SuffixesTwoToNinetyNine() {
        List<Username> candidates = UsernameSuggestions.suffixed("valentin.le.gall");

        assertThat(candidates).hasSize(98);
        assertThat(candidates.getFirst().value()).isEqualTo("valentin.le.gall2");
        assertThat(candidates.getLast().value()).isEqualTo("valentin.le.gall99");
    }

    @Test
    @DisplayName("AUTH-04 : étape 5, la base est tronquée pour laisser place au suffixe, sans finir par un séparateur")
    void auth04SuffixTruncatesTheBase() {
        List<Username> fromFull = UsernameSuggestions.suffixed("abcdefghijklmnopqrst");
        List<Username> fromDotted = UsernameSuggestions.suffixed("abcdefghijklmnopqr.t");

        assertThat(fromFull.getFirst().value()).isEqualTo("abcdefghijklmnopqrs2");
        assertThat(fromFull.get(8).value()).isEqualTo("abcdefghijklmnopqr10");
        assertThat(fromDotted.getFirst().value()).isEqualTo("abcdefghijklmnopqr2");
    }

    @Test
    @DisplayName("AUTH-04 : étape 5, puis 4 chiffres aléatoires après la base tronquée à 16 caractères")
    void auth04FourRandomDigits() {
        RandomDigits random = length -> "0042".substring(0, length);

        assertThat(UsernameSuggestions.randomSuffixed("abcdefghijklmnopqrst", random)
                        .value())
                .isEqualTo("abcdefghijklmnop0042");
        assertThat(UsernameSuggestions.randomSuffixed("user", random).value()).isEqualTo("user0042");
    }
}
