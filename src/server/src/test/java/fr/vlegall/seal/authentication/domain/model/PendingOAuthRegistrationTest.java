package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-32 : inscription OAuth en attente pendant 10 minutes. */
@DisplayName("AUTH-32 : inscription OAuth en attente")
class PendingOAuthRegistrationTest {

    private static final Instant RETURN = Instant.parse("2026-11-20T15:00:00Z");
    private static final ExternalIdentity IDENTITY =
            ExternalIdentity.of(OAuthProvider.GOOGLE, "1", "v@exemple.ca", true, "Valentin");

    @Test
    @DisplayName("AUTH-32 : l'attente vit 10 minutes à partir du retour du fournisseur, borne exclue")
    void auth32AliveForTenMinutes() {
        PendingOAuthRegistration pending = new PendingOAuthRegistration(IDENTITY, RETURN);

        assertThat(pending.expiresAt()).isEqualTo(RETURN.plus(Duration.ofMinutes(10)));
        assertThat(pending.isAlive(RETURN)).isTrue();
        assertThat(pending.isAlive(pending.expiresAt().minusNanos(1))).isTrue();
        assertThat(pending.isAlive(pending.expiresAt())).isFalse();
    }

    @Test
    @DisplayName("ADR 0032, point 8 : l'attente garde l'instant du retour, qui date le facteur")
    void adr0032KeepsTheReturnInstant() {
        assertThat(new PendingOAuthRegistration(IDENTITY, RETURN).authenticatedAt())
                .isEqualTo(RETURN);
    }

    @Test
    @DisplayName(
            "ADR 0008 : le domaine ne dépend pas du format de la session (ni l'attente ni l'identité ne sont sérialisables)")
    void adr0008DomainIsNotSerializable() {
        assertThat(Serializable.class.isAssignableFrom(PendingOAuthRegistration.class))
                .isFalse();
        assertThat(Serializable.class.isAssignableFrom(ExternalIdentity.class)).isFalse();
    }
}
