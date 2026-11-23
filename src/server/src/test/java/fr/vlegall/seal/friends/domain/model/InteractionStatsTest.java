package fr.vlegall.seal.friends.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FRD-10 : statistiques d'interaction")
class InteractionStatsTest {

    @Test
    @DisplayName("FRD-10 : aucune interaction, c'est aucune date et zéro message")
    void frd10NoneHasNoDateAndNoMessage() {
        assertThat(InteractionStats.NONE.lastInteractionAt()).isEmpty();
        assertThat(InteractionStats.NONE.count30d()).isZero();
    }

    @Test
    @DisplayName("FRD-10 : un nombre de messages négatif est refusé")
    void frd10NegativeCountIsRefused() {
        assertThatThrownBy(() -> new InteractionStats(Optional.empty(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
