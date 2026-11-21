package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthLoginOutcome;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthEventPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-30 à AUTH-33 : issue d'un retour OAuth, avec des doublures des ports. */
@DisplayName("AUTH-31 : issue d'un retour OAuth")
class OAuthLoginHandlerTest {

    private final Map<String, UserId> linked = new HashMap<>();
    private final Set<UserId> active = new HashSet<>();
    private final Set<UserId> pending = new HashSet<>();
    private final Map<String, UserId> activeEmails = new HashMap<>();
    private final List<String> emailLookups = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    private final OAuthIdentityRepository identities = new OAuthIdentityRepository() {
        @Override
        public Optional<UserId> findUser(OAuthProvider provider, String subject) {
            return Optional.ofNullable(linked.get(provider + ":" + subject));
        }

        @Override
        public void link(UserId user, ExternalIdentity identity, Instant linkedAt) {
            throw new UnsupportedOperationException("le retour OAuth ne lie rien");
        }
    };

    private final OAuthAccountLookupPort accounts = new OAuthAccountLookupPort() {
        @Override
        public AccountLoginState state(UserId user) {
            if (active.contains(user)) {
                return AccountLoginState.ACTIVE;
            }
            return pending.contains(user) ? AccountLoginState.PENDING_EMAIL : AccountLoginState.UNUSABLE;
        }

        @Override
        public boolean activeAccountOwns(String email) {
            emailLookups.add(email);
            return activeEmails.containsKey(email);
        }
    };

    private final OAuthEventPort journal = new OAuthEventPort() {
        @Override
        public void oauthLoginSucceeded(UserId user, OAuthProvider provider) {
            events.add("réussie:" + provider + ":" + user.value());
        }

        @Override
        public void oauthLoginFailed(@Nullable UserId user, @Nullable OAuthProvider provider, String cause) {
            events.add("refusée:" + provider + ":" + cause);
        }
    };

    private final OAuthLoginHandler handler = new OAuthLoginHandler(identities, accounts, journal);

    private static ExternalIdentity google(String subject, String email, boolean verified) {
        return ExternalIdentity.of(OAuthProvider.GOOGLE, subject, email, verified, "Nom");
    }

    private UserId linkedTo(String subject) {
        UserId user = new UserId(UUID.randomUUID());
        linked.put(OAuthProvider.GOOGLE + ":" + subject, user);
        return user;
    }

    @Test
    @DisplayName("AUTH-31 : sujet lié à un compte actif, session complète et connexion journalisée")
    void auth31LinkedActiveAccount() {
        UserId user = linkedTo("1");
        active.add(user);

        assertThat(handler.resolve(google("1", "x@exemple.ca", false)))
                .isEqualTo(new OAuthLoginOutcome.SignedIn(user, true));
        assertThat(events).containsExactly("réussie:GOOGLE:" + user.value());
        assertThat(emailLookups)
                .as("un compte lié ne se cherche jamais par courriel")
                .isEmpty();
    }

    @Test
    @DisplayName("AUTH-16 : sujet lié à un compte en attente, session restreinte")
    void auth16LinkedPendingAccount() {
        UserId user = linkedTo("2");
        pending.add(user);

        assertThat(handler.resolve(google("2", "x@exemple.ca", true)))
                .isEqualTo(new OAuthLoginOutcome.SignedIn(user, false));
    }

    @Test
    @DisplayName("ACC-07 : sujet lié à un compte ni actif ni en attente, refusé et journalisé")
    void acc07LinkedUnusableAccountRejected() {
        UserId user = linkedTo("3");

        assertThat(handler.resolve(google("3", "x@exemple.ca", true))).isEqualTo(new OAuthLoginOutcome.Rejected());
        assertThat(events).containsExactly("refusée:GOOGLE:account_unusable");
    }

    @Test
    @DisplayName("AUTH-33 : sujet inconnu, courriel vérifié d'un compte actif : aucune liaison, refus journalisé")
    void auth33VerifiedEmailInUse() {
        activeEmails.put("pris@exemple.ca", new UserId(UUID.randomUUID()));

        assertThat(handler.resolve(google("4", "pris@exemple.ca", true))).isEqualTo(new OAuthLoginOutcome.EmailInUse());
        assertThat(events).containsExactly("refusée:GOOGLE:email_in_use");
    }

    @Test
    @DisplayName("ADR 0032 : courriel non vérifié, l'adresse n'est pas même cherchée : attente, comme à l'inscription")
    void adr0032UnverifiedEmailIsNotLookedUp() {
        activeEmails.put("pris@exemple.ca", new UserId(UUID.randomUUID()));
        ExternalIdentity identity = google("5", "pris@exemple.ca", false);

        assertThat(handler.resolve(identity)).isEqualTo(new OAuthLoginOutcome.Unlinked(identity));
        assertThat(emailLookups).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("AUTH-32 : sujet inconnu, courriel libre : premier passage")
    void auth32FirstPass() {
        ExternalIdentity identity = google("6", "libre@exemple.ca", true);

        assertThat(handler.resolve(identity)).isEqualTo(new OAuthLoginOutcome.Unlinked(identity));
        assertThat(emailLookups).containsExactly("libre@exemple.ca");
    }

    @Test
    @DisplayName("SEC-20 : un retour refusé avant toute identité est journalisé avec sa cause")
    void sec20RejectedReturnJournaled() {
        handler.rejected(OAuthProvider.GITHUB, "invalid_grant");
        handler.rejected(null, "oauth_error");

        assertThat(events).containsExactly("refusée:GITHUB:invalid_grant", "refusée:null:oauth_error");
    }
}
