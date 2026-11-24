package fr.vlegall.seal.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GRP-01, SEC-01 : nom d'un groupe")
class GroupNameTest {

    @Test
    @DisplayName("GRP-01 : nom normalisé en NFC, 1 à 64 points de code")
    void grp01NameIsNfcFromOneToSixtyFour() {
        assertThat(GroupName.parse("Cafe\u0301").value()).isEqualTo("Caf\u00E9");
        assertThat(GroupName.parse("x".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> GroupName.parse("x".repeat(65))).isInstanceOf(ConversationException.InvalidName.class);
        assertThatThrownBy(() -> GroupName.parse("")).isInstanceOf(ConversationException.InvalidName.class);
    }

    @Test
    @DisplayName("SEC-01 : caractère de direction ou de contrôle refusé")
    void sec01TrappedCharactersAreRefused() {
        assertThatThrownBy(() -> GroupName.parse("Ski\u202E")).isInstanceOf(ConversationException.InvalidName.class);
        assertThatThrownBy(() -> GroupName.parse("Ski\u0007")).isInstanceOf(ConversationException.InvalidName.class);
    }

    @Test
    @DisplayName("ACC-03, ADR 0040 : caractère invisible ou d'usage privé refusé dans un nom de groupe")
    void acc03InvisibleCharacterIsRefused() {
        for (int invisible : new int[] {0x200B, 0xFEFF, 0xE000}) {
            assertThatThrownBy(() -> GroupName.parse("S" + Character.toString(invisible) + "ki"))
                    .isInstanceOf(ConversationException.InvalidName.class);
        }
    }
}
