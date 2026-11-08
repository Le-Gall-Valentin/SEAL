package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordTest {

    @Test
    @DisplayName("AUTH-11 : le mot de passe est normalisé en NFKC")
    void auth11PasswordIsNfkcNormalised() {
        assertThat(Password.fromInput("ﬁ①").value()).isEqualTo("fi1");
    }

    @Test
    @DisplayName("SEC-21 : toString() ne révèle pas le mot de passe")
    void sec21ToStringHidesThePassword() {
        assertThat(Password.fromInput("fjord-tuque-raquette").toString()).doesNotContain("fjord");
    }
}
