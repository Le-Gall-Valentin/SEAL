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
    @DisplayName("ACC-03, ADR 0040 : caractères de format invisibles et usage privé refusés ; ZWJ, ZWNJ et VS16 permis")
    void acc03InvisibleFormatAndPrivateUseAreRefused() {
        // U+200B, U+2060, U+FEFF, U+00AD, étiquettes U+E0001 et U+E0041, usage privé des trois plages.
        for (int invisible : new int[] {0x200B, 0x2060, 0xFEFF, 0x00AD, 0xE0001, 0xE0041, 0xE000, 0xF0000, 0x10FFFD}) {
            String name = "S" + Character.toString(invisible) + "ki";
            assertThat(DisplayNameRule.isForbidden(invisible))
                    .as("U+%04X", invisible)
                    .isTrue();
            assertThat(DisplayNameRule.isValid(name)).as("U+%04X", invisible).isFalse();
            assertThat(DisplayNameRule.isValid(name, 64))
                    .as("U+%04X", invisible)
                    .isFalse();
        }
        String family = Character.toString(0x1F469) + Character.toString(0x200D) + Character.toString(0x1F467);
        assertThat(DisplayNameRule.isValid(family)).isTrue();
        assertThat(DisplayNameRule.isValid("a" + Character.toString(0x200C) + "b"))
                .isTrue();
        assertThat(DisplayNameRule.isValid(Character.toString(0x2764) + Character.toString(0xFE0F)))
                .isTrue();
    }

    @Test
    @DisplayName("AUTH-10 : sans longueur donnée, le nom affiché garde ses 50 points de code")
    void auth10DisplayNameKeepsFiftyCodePoints() {
        assertThat(DisplayNameRule.isValid("x".repeat(50))).isTrue();
        assertThat(DisplayNameRule.isValid("x".repeat(51))).isFalse();
    }
}
