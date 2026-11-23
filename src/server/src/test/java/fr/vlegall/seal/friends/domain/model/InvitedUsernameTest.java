package fr.vlegall.seal.friends.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ADR 0037, point 6 : saisie de l'identifiant à inviter")
class InvitedUsernameTest {

    @ParameterizedTest
    @CsvSource({"sarah.belanger, sarah.belanger", "'  @Sarah.Belanger ', sarah.belanger", "@LEA_2, lea_2"})
    @DisplayName("ADR 0037 : espaces autour, @ initial et majuscules sont retirés")
    void adr0037InputIsNormalized(String input, String expected) {
        assertThat(InvitedUsername.normalize(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "@", "@@sarah", "ab", "deux mots", "sarah..b", "élodie", "abcdefghijklmnopqrstu"})
    @DisplayName("AUTH-01 : toute autre saisie est refusée")
    void auth01OtherInputIsRejected(String input) {
        assertThat(InvitedUsername.normalize(input)).isEmpty();
    }
}
