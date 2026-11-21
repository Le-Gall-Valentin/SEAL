package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
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
        ExternalIdentity identity = google("1234567890", "vale\u0301ntin@exemple.ca", "Valentin Dupont");

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
        assertThat(google("1", "v@exemple.ca", "Vale\u202Entin\u0000 ").displayName())
                .isEqualTo("Valentin");
        assertThat(google("1", "v@exemple.ca", "🦭".repeat(60)).displayName()).isEqualTo("🦭".repeat(50));
        assertThat(google("1", "v@exemple.ca", "e\u0301").displayName()).isEqualTo("é");
        assertThat(ExternalIdentity.of(OAuthProvider.GITHUB, "1", "v@exemple.ca", false, null)
                        .displayName())
                .isEmpty();
    }

    @Test
    @DisplayName("ACC-03 : le nom proposé suit la règle du nom affiché : ZWJ des émojis gardé, nom invisible abandonné")
    void acc03SuggestedNameFollowsTheDisplayNameRule() {
        String family = "👨\u200D👩\u200D👧";
        assertThat(google("1", "v@exemple.ca", family).displayName()).isEqualTo(family);
        assertThat(google("1", "v@exemple.ca", "\u3164").displayName()).isEmpty();
        assertThat(google("1", "v@exemple.ca", "\u200F\u202E").displayName()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-30 : aucun nom du fournisseur ne refuse l'identité ; le nom proposé est stable (idempotent)")
    void auth30SuggestedNameNeverBlocksAndIsIdempotent() {
        String[] names = {
            "Jose\u200D\u0301",
            "Re\u0000\u0301",
            "a\u202E\u0301b",
            "\u0301",
            "x".repeat(49) + "e\u0301\u0302",
            " \u200B ",
            "Valentin Dupont",
            "👨\u200D👩",
            "\uD800",
            "각"
        };
        for (String name : names) {
            String suggested = google("1", "v@exemple.ca", name).displayName();
            assertThat(google("1", "v@exemple.ca", suggested).displayName())
                    .as(name)
                    .isEqualTo(suggested);
            assertThat(new ExternalIdentity(OAuthProvider.GOOGLE, "1", "v@exemple.ca", true, suggested))
                    .as(name)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("SEC-21 : toString masque le courriel")
    void sec21EmailMaskedInToString() {
        assertThat(google("1", "valentin@exemple.ca", "Valentin").toString()).doesNotContain("valentin@exemple.ca");
    }

    /** Une longue suite de marques combinantes de classes alternées : le pire cas de la normalisation NFC. */
    private static String combiningMarks(int pairs) {
        return (Character.toString(0x0301) + Character.toString(0x0316)).repeat(pairs);
    }

    @Test
    @DisplayName("SEC-01, A06 : un nom du fournisseur de plus de 1 024 caractères ne donne aucune proposition")
    void sec01OverlongNameGivesNoSuggestion() {
        assertThat(ExternalIdentity.suggestedName("a".repeat(ExternalIdentity.RAW_MAX_LENGTH)))
                .isEqualTo("a".repeat(50));
        assertThat(ExternalIdentity.suggestedName("a".repeat(ExternalIdentity.RAW_MAX_LENGTH + 1)))
                .isEmpty();
    }

    @Test
    @DisplayName("SEC-01, A06 : un nom ou un courriel hostile du fournisseur est écarté sans être normalisé")
    void sec01HostileInputIsBoundedBeforeNormalization() {
        String hostile = "a" + combiningMarks(100_000);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThat(ExternalIdentity.suggestedName(hostile)).isEmpty();
            assertThatThrownBy(
                            () -> ExternalIdentity.of(OAuthProvider.GITHUB, "42", hostile + "@exemple.ca", true, "Nom"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("courriel du fournisseur invalide");
        });
    }
}
