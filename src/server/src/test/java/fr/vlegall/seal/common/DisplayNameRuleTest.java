package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GRP-01, ADR 0039 (point 6) : règle du nom, à la longueur de chaque usage")
class DisplayNameRuleTest {

    @Test
    @DisplayName("GRP-01 : un nom de groupe compte 1 à 64 points de code")
    void grp01GroupNameCountsOneToSixtyFourCodePoints() {
        assertThat(DisplayNameRule.isValid("x".repeat(64), 64)).isTrue();
        assertThat(DisplayNameRule.isValid("é".repeat(64), 64)).isTrue();
        assertThat(DisplayNameRule.isValid("x".repeat(65), 64)).isFalse();
        assertThat(DisplayNameRule.isValid("", 64)).isFalse();
    }

    @Test
    @DisplayName("SEC-01 : à toute longueur, caractère de direction, de contrôle ou nom invisible refusés")
    void sec01TrappedCharactersAreRefusedAtAnyLength() {
        assertThat(DisplayNameRule.isValid("Ski\u202Eedrof", 64)).isFalse();
        assertThat(DisplayNameRule.isValid("Ski\u0007", 64)).isFalse();
        assertThat(DisplayNameRule.isValid("   ", 64)).isFalse();
    }

    @Test
    @DisplayName("AUTH-10 : sans longueur donnée, le nom affiché garde ses 50 points de code")
    void auth10DisplayNameKeepsFiftyCodePoints() {
        assertThat(DisplayNameRule.isValid("x".repeat(50))).isTrue();
        assertThat(DisplayNameRule.isValid("x".repeat(51))).isFalse();
    }
}
