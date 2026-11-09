package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private static final Blocklist BLOCKLIST = Set.of("correcthorsebatterystaple")::contains;
    private static final List<String> NO_WORDS = List.of();
    private static final List<String> VALENTIN = List.of("valentin.le.gall", "valentin");

    private static boolean accepts(String raw, List<String> personalWords) {
        return PasswordPolicy.accepts(Password.fromInput(raw), personalWords, BLOCKLIST);
    }

    @Test
    @DisplayName("AUTH-11 : 15 points de code au minimum, 128 au maximum, sans règle de composition")
    void auth11LengthBounds() {
        assertThat(accepts("a".repeat(14), NO_WORDS)).isFalse();
        assertThat(accepts("a".repeat(15), NO_WORDS)).isTrue();
        assertThat(accepts("a".repeat(128), NO_WORDS)).isTrue();
        assertThat(accepts("a".repeat(129), NO_WORDS)).isFalse();
        assertThat(accepts("🦭".repeat(15), NO_WORDS)).isTrue();
    }

    @Test
    @DisplayName("AUTH-11 : la longueur se compte après NFKC ; 120 points qui en deviennent 130 sont refusés")
    void auth11LengthCountsAfterNfkc() {
        assertThat(accepts("a".repeat(110) + "㎏".repeat(10), NO_WORDS)).isFalse();
        assertThat(accepts("ﬁ".repeat(8), NO_WORDS)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"correcthorsebatterystaple", "CorrectHorseBatteryStaple", "Ｃorrecthorsebatterystaple"})
    @DisplayName("AUTH-11 : une entrée de la liste de blocage est refusée, quelle que soit sa casse ou sa forme")
    void auth11BlocklistIsCaseAndFormInsensitive(String raw) {
        assertThat(accepts(raw, NO_WORDS)).isFalse();
    }

    @Test
    @DisplayName("AUTH-11 : l'identifiant et la partie locale du courriel ne comptent pas (ADR 0019)")
    void auth11PersonalWordsDoNotCount() {
        assertThat(accepts("valentin.le.gall2026!x", VALENTIN)).isFalse();
        assertThat(accepts("Valentin.Le.Gall-VALENTIN-2026", VALENTIN)).isFalse();
        assertThat(accepts("valentin.le.gall2026!x", NO_WORDS)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sealsealsealseal", "SEALuqacSEALuqac", "sesealalsesealalsesealal", "seal seal seal seal"})
    @DisplayName("AUTH-11 : « seal » et « uqac » ne comptent pas, même imbriqués")
    void auth11ServiceWordsDoNotCount(String raw) {
        assertThat(accepts(raw, NO_WORDS)).isFalse();
    }

    @Test
    @DisplayName("AUTH-11 : une phrase de passe qui contient « seal » reste acceptée")
    void auth11PassphraseContainingSealIsAccepted() {
        assertThat(accepts("le phoque nage dans le port de seal", VALENTIN)).isTrue();
    }

    @Test
    @DisplayName("AUTH-11 : un mot du contexte de moins de 3 caractères est ignoré")
    void auth11ShortPersonalWordsAreIgnored() {
        assertThat(accepts("jojojojojojojojo", List.of("jo"))).isTrue();
    }

    @Test
    @DisplayName("AUTH-11 : une demi-paire isolée est refusée (PASSWORD_TOO_WEAK), jamais transmise au hachage")
    void auth11LoneSurrogateIsRefused() {
        String passphrase = "fjord-tuque-raquette";
        assertThat(accepts(Character.toString(0xD800) + passphrase, NO_WORDS)).isFalse();
        assertThat(accepts(passphrase + Character.toString(0xDC00), NO_WORDS)).isFalse();
        assertThat(accepts(Character.toString(0xDC00) + Character.toString(0xD800) + passphrase, NO_WORDS))
                .isFalse();
        assertThat(accepts("🦭" + passphrase, NO_WORDS)).as("paire complète").isTrue();
    }

    @Test
    @DisplayName("AUTH-11 : le mot du contexte le plus long est retiré d'abord (PROTOCOL.md, section 3)")
    void auth11LongestContextWordIsRemovedFirst() {
        // Identifiant « ann », partie locale « annabelle » : retirer « ann » d'abord laisserait « abelle sur le
        // pont », 18 points de code ; retirer « annabelle » d'abord n'en laisse que 12.
        assertThat(accepts("annabelle sur le pont", List.of("ann", "annabelle")))
                .isFalse();
        assertThat(accepts("annabelle sur le pont", List.of("annabelle", "ann")))
                .isFalse();
        assertThat(accepts("annabelle sur le pont du lac", List.of("ann", "annabelle")))
                .isTrue();
    }

    @ParameterizedTest(name = "{0} → {3}")
    @CsvSource({
        "sealsealsealseal, valentin, valentin, false",
        "sesealalsesealalsesealal, valentin, valentin, false",
        "valentin.le.gall2026, valentin.le.gall, v, false",
        "annabelle sur le pont, ann, annabelle, false",
        "annabelle sur le pont du lac, ann, annabelle, true",
        "abababzzzzzzzzzzzzz, aba, bab, false",
        "abababzzzzzzzzzzzzz, bab, aba, true",
        "le phoque nage dans le port de seal, valentin, valentin, true"
    })
    @DisplayName("AUTH-11 : les exemples de PROTOCOL.md (section 3), que le client vérifiera aussi")
    void auth11ProtocolExamples(String raw, String username, String localPart, boolean expected) {
        assertThat(accepts(raw, List.of(username, localPart))).isEqualTo(expected);
    }
}
