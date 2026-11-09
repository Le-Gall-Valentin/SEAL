package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "valentin@exemple.ca",
                "Valéntin.Le-Gall+uqac@exemple.ca",
                "a@b.co",
                "o'neil_x-y{z}@sub.exemple-2.qc.ca"
            })
    @DisplayName("AUTH-10 : une adresse ordinaire est acceptée telle que saisie")
    void auth10AcceptsOrdinaryAddresses(String raw) {
        assertThat(EmailAddress.parse(raw)).map(EmailAddress::value).contains(raw);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "sans-arobase",
                "a@b",
                "a@@b.ca",
                "a@b@c.ca",
                "@b.ca",
                "a@.ca",
                "a@b.",
                "a@b..ca",
                "a@-b.ca",
                "a@b-.ca",
                "a@exem_ple.ca",
                "a@exémple.ca",
                "a b@c.ca",
                ".a@b.ca",
                "a.@b.ca",
                "a..b@c.ca",
                "<a>@b.ca",
                "a\"b@c.ca",
                "a\u0000@b.ca",
                "a\u202E@b.ca",
                "a\n@b.ca"
            })
    @DisplayName("SEC-01 : une adresse mal formée est refusée")
    void sec01RejectsMalformedAddresses(String raw) {
        assertThat(EmailAddress.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("SEC-01 : 254 caractères au plus, 64 pour la partie locale et 63 par libellé du domaine")
    void sec01EnforcesLengthLimits() {
        String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".ca";
        assertThat(EmailAddress.parse("a".repeat(64) + "@" + domain)).isPresent();
        assertThat(EmailAddress.parse("a".repeat(64) + "@" + domain + "x")).isEmpty();
        assertThat(EmailAddress.parse("a".repeat(65) + "@b.ca")).isEmpty();
        assertThat(EmailAddress.parse("a@" + "b".repeat(64) + ".ca")).isEmpty();
    }

    @Test
    @DisplayName("SEC-01 : l'adresse est normalisée en NFC")
    void sec01AddressIsNfcNormalised() {
        assertThat(EmailAddress.parse("e\u0301@b.ca")).map(EmailAddress::value).contains("é@b.ca");
    }

    @Test
    @DisplayName("AUTH-04 : la partie locale s'arrête au premier + ou @")
    void auth04LocalPartStopsAtPlusOrAt() {
        assertThat(EmailAddress.parse("Valéntin.Le-Gall+uqac@exemple.ca")
                        .orElseThrow()
                        .localPart())
                .isEqualTo("Valéntin.Le-Gall");
        assertThat(EmailAddress.parse("valentin@exemple.ca").orElseThrow().localPart())
                .isEqualTo("valentin");
    }

    @Test
    @DisplayName("SEC-21 : toString() ne révèle pas l'adresse")
    void sec21ToStringHidesTheAddress() {
        assertThat(EmailAddress.parse("valentin@exemple.ca").orElseThrow().toString())
                .doesNotContain("valentin")
                .doesNotContain("exemple");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\uD800@b.ca", "a\u0378@b.ca", "a\uE000@b.ca", "a\u200B@b.ca"})
    @DisplayName(
            "SEC-01 : la partie locale refuse les demi-paires isolées, les points non attribués, l'usage privé et les caractères de format")
    void sec01LocalPartRefusesMalformedText(String raw) {
        assertThat(EmailAddress.parse(raw)).isEmpty();
    }
}
