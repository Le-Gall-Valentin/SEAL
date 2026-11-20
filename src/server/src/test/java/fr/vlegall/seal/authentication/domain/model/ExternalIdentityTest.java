package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-31 : identité rendue par un fournisseur, telle que SEAL la garde. */
@DisplayName("AUTH-31 : identité externe")
class ExternalIdentityTest {

    private static ExternalIdentity google(String subject, String email, String name) {
        return ExternalIdentity.of(OAuthProvider.GOOGLE, subject, email, true, name);
    }

    @Test
    @DisplayName("AUTH-31 : fournisseur, sujet, courriel en NFC, vérification et nom proposé")
    void auth31KeepsTheProviderData() {
        ExternalIdentity identity = google("1234567890", "valéntin@exemple.ca", "Valentin Dupont");

        assertThat(identity.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(identity.subject()).isEqualTo("1234567890");
        assertThat(identity.email()).isEqualTo("valéntin@exemple.ca");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.displayName()).isEqualTo("Valentin Dupont");
    }

    @Test
    @DisplayName("AUTH-31 : un sujet vide, trop long, avec espace ou caractère de contrôle est refusé")
    void auth31InvalidSubjectRefused() {
        for (String subject : new String[] {"", " ", "12 34", "12\n34", "x".repeat(256), "é"}) {
            assertThatThrownBy(() -> google(subject, "valentin@exemple.ca", "Valentin"))
                    .as(subject)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(google("x".repeat(255), "valentin@exemple.ca", "Valentin").subject())
                .hasSize(255);
    }

    @Test
    @DisplayName("AUTH-31 : un courriel mal formé est refusé, sans que le message le recopie (SEC-21)")
    void auth31InvalidEmailRefused() {
        for (String email :
                new String[] {"", "pas-un-courriel", "a@b", "valentin@exemple.ca\n", "a".repeat(250) + "@x.ca"}) {
            assertThatThrownBy(() -> google("1", email, "Valentin"))
                    .as(email)
                    .isInstanceOf(IllegalArgumentException.class)
                    .message()
                    .doesNotContain("pas-un-courriel");
        }
    }

    @Test
    @DisplayName(
            "ACC-03 : le nom proposé perd ses caractères de contrôle et de direction, et 50 points de code au plus")
    void acc03SuggestedNameIsCleaned() {
        assertThat(google("1", "v@exemple.ca", "Vale‮ntin\u0000 ").displayName())
                .isEqualTo("Valentin");
        assertThat(google("1", "v@exemple.ca", "🦭".repeat(60)).displayName()).isEqualTo("🦭".repeat(50));
        assertThat(google("1", "v@exemple.ca", "é").displayName()).isEqualTo("é");
        assertThat(ExternalIdentity.of(OAuthProvider.GITHUB, "1", "v@exemple.ca", false, null)
                        .displayName())
                .isEmpty();
    }

    @Test
    @DisplayName("SEC-21 : toString masque le courriel")
    void sec21EmailMaskedInToString() {
        assertThat(google("1", "valentin@exemple.ca", "Valentin").toString()).doesNotContain("valentin@exemple.ca");
    }
}
