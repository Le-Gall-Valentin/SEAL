package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthException;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.model.OAuthRegistration;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.authentication.domain.port.out.OAuthLinkEventPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.MutableClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-32 : compte créé, identité liée et liaison journalisée, dans cet ordre, tout ou rien. */
@DisplayName("AUTH-32 : fin d'une inscription OAuth")
class CompleteOAuthRegistrationHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private final List<String> steps = new ArrayList<>();
    private final UserId created = new UserId(UUID.randomUUID());
    private boolean alreadyLinked;

    private final OAuthAccountPort accounts = (username, displayName, email, emailVerified) -> {
        steps.add("compte:" + username + ":" + emailVerified);
        return created;
    };

    private final OAuthIdentityRepository identities = new OAuthIdentityRepository() {
        @Override
        public Optional<UserId> findUser(OAuthProvider provider, String subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLinked(UserId user, OAuthProvider provider) {

            throw new UnsupportedOperationException("réservé à la ré-authentification (ADR 0034)");
        }

        @Override
        public void link(UserId user, ExternalIdentity identity, Instant linkedAt) {
            if (alreadyLinked) {
                throw new OAuthException.AlreadyLinked();
            }
            steps.add("liaison:" + user.value() + ":" + identity.subject() + ":" + linkedAt);
        }
    };

    private final OAuthLinkEventPort journal =
            (user, provider) -> steps.add("journal:" + provider + ":" + user.value());

    private final CompleteOAuthRegistrationHandler handler =
            new CompleteOAuthRegistrationHandler(accounts, identities, journal, MutableClock.fixedAt(NOW));

    private static ExternalIdentity github(boolean verified) {
        return ExternalIdentity.of(OAuthProvider.GITHUB, "42", "octo@exemple.ca", verified, "Octo");
    }

    @Test
    @DisplayName(
            "AUTH-32 : compte, puis liaison datée, puis journal ; la session est complète si le courriel est vérifié")
    void auth32CreatesLinksThenJournals() {
        OAuthRegistration registration = handler.complete(github(true), "octo", "Octo");

        assertThat(registration).isEqualTo(new OAuthRegistration(created, true));
        assertThat(steps)
                .containsExactly(
                        "compte:octo:true",
                        "liaison:" + created.value() + ":42:" + NOW,
                        "journal:GITHUB:" + created.value());
        assertThat(handler.complete(github(false), "octo", "Octo").active()).isFalse();
    }

    @Test
    @DisplayName("AUTH-31 : identité liée entre-temps, le refus remonte et rien n'est journalisé")
    void auth31AlreadyLinkedPropagates() {
        alreadyLinked = true;

        assertThatThrownBy(() -> handler.complete(github(true), "octo", "Octo"))
                .isInstanceOf(OAuthException.AlreadyLinked.class);
        assertThat(steps).containsExactly("compte:octo:true");
    }
}
