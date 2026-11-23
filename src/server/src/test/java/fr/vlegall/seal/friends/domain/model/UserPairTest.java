package fr.vlegall.seal.friends.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD-01 : une paire d'utilisateurs ne dépend pas du sens, comme {@code friendships} et son index unique. */
@DisplayName("FRD-01 : paire d'utilisateurs")
class UserPairTest {

    private static final UserId LOW = new UserId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final UserId HIGH = new UserId(UUID.fromString("ffffffff-0000-7000-8000-000000000001"));

    @Test
    @DisplayName("FRD-01 : la paire est la même dans les deux sens")
    void frd01PairIsSymmetric() {
        assertThat(UserPair.of(LOW, HIGH)).isEqualTo(UserPair.of(HIGH, LOW));
        assertThat(UserPair.of(HIGH, LOW).low()).isEqualTo(LOW);
    }

    @Test
    @DisplayName("FRD-01 : l'ordre suit celui de PostgreSQL (octets non signés), pas UUID.compareTo")
    void frd01OrderFollowsPostgresql() {
        // UUID.compareTo compare des long signés : ffffffff-… y passe avant 00000000-…, à l'inverse de PostgreSQL.
        assertThat(HIGH.value().compareTo(LOW.value())).isNegative();

        assertThat(UserPair.of(HIGH, LOW).high()).isEqualTo(HIGH);
    }

    @Test
    @DisplayName("FRD-01 : l'autre membre de la paire se déduit de l'un")
    void frd01OtherMember() {
        UserPair pair = UserPair.of(LOW, HIGH);

        assertThat(pair.other(LOW)).isEqualTo(HIGH);
        assertThat(pair.other(HIGH)).isEqualTo(LOW);
        assertThatThrownBy(() -> pair.other(new UserId(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CANNOT_INVITE_SELF : une paire de deux fois le même compte n'existe pas")
    void samePersonTwiceIsNotAPair() {
        assertThatThrownBy(() -> UserPair.of(LOW, LOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserPair(HIGH, LOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
