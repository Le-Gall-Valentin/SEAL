package fr.vlegall.seal.authentication.application.handler;

import static fr.vlegall.seal.authentication.application.handler.PasswordResetFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.ResetOutcome;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResetPasswordHandlerTest {

    private static final String NEW_PASSWORD = "nouvelle-phrase-de-passe";

    private final PasswordResetFixture fixture = new PasswordResetFixture();
    private final ResetPasswordHandler handler = new ResetPasswordHandler(
            fixture.tokens,
            fixture.accounts,
            fixture.preparation(),
            new PasswordResetWriter(fixture.tokens, fixture.credentials),
            fixture.sessions,
            fixture.journal,
            fixture.clock);

    @Test
    @DisplayName("AUTH-70 : le succès remplace le haché, consomme le lien, puis révoque les sessions et journalise")
    void auth70SuccessReplacesHashThenRevokesAndJournals() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());

        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.Done());

        assertThat(fixture.credentials.hashes()).containsEntry(valentin.user(), "hash(" + NEW_PASSWORD + ")");
        assertThat(fixture.tokens.findLive(token.hash(), fixture.clock.instant()))
                .isEmpty();
        assertThat(fixture.calls)
                .as("Argon2id hors transaction, puis l'écriture, puis la révocation avant le journal (ADR 0022)")
                .containsExactly(
                        "hachage",
                        "consommation",
                        "révocation:" + valentin.user().value(),
                        "journal:" + valentin.user().value());
    }

    @Test
    @DisplayName("AUTH-70 : le succès invalide aussi tout autre lien encore valable du compte")
    void auth70SuccessInvalidatesEveryOtherLink() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        byte[] other = ResetToken.of(fixture.random().next(ResetToken.BYTES)).hash();
        fixture.rows.add(new PasswordResetFixture.Row(
                UUID.randomUUID(), valentin.user(), other, NOW.plus(Duration.ofMinutes(30)), null));

        handler.reset(token.value(), NEW_PASSWORD);

        assertThat(fixture.tokens.findLive(other, fixture.clock.instant())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "pas-un-jeton", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("AUTH-70 : un lien mal formé ou inconnu est refusé sans aucun calcul Argon2id")
    void auth70UnknownLinkCostsNoHash(String token) {
        fixture.active("valentin", true);

        assertThat(handler.reset(token, NEW_PASSWORD)).isEqualTo(new ResetOutcome.InvalidToken());
        assertThat(fixture.calls).isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : un lien expiré ou déjà utilisé est refusé sans calcul ni révocation")
    void auth70ExpiredOrUsedLinkIsRefused() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        handler.reset(token.value(), NEW_PASSWORD);
        fixture.calls.clear();
        ResetToken expiring = fixture.issue(valentin.user());
        fixture.clock.advance(Duration.ofMinutes(30));

        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.InvalidToken());
        assertThat(handler.reset(expiring.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.InvalidToken());
        assertThat(fixture.calls).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"court", "correcthorsebatterystaple", "valentinvalentinvalentin"})
    @DisplayName("AUTH-11 : un mot de passe refusé ne consomme pas le lien, qui reste utilisable (ADR 0022)")
    void auth11WeakPasswordKeepsTheLink(String weak) {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());

        assertThat(handler.reset(token.value(), weak)).isEqualTo(new ResetOutcome.WeakPassword());
        assertThat(fixture.calls).isEmpty();
        assertThat(fixture.tokens.findLive(token.hash(), NOW)).isPresent();
        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.Done());
    }

    @Test
    @DisplayName("AUTH-70 : un lien qui expire pendant le calcul Argon2id est refusé, sans écriture ni révocation")
    void auth70LinkExpiringDuringTheHashIsRefused() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        fixture.clock.advance(Duration.ofMinutes(29));
        fixture.duringHash = () -> fixture.clock.advance(Duration.ofMinutes(2));

        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.InvalidToken());
        assertThat(fixture.credentials.hashes()).containsEntry(valentin.user(), "hash(ancien-mot-de-passe-long)");
        assertThat(fixture.calls).containsExactly("hachage", "consommation");
    }

    @Test
    @DisplayName("AUTH-70 : le lien d'un compte qui n'est plus actif est refusé sans calcul")
    void auth70LinkOfAnInactiveAccountIsRefused() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        fixture.contacts.remove(valentin.user());

        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.InvalidToken());
        assertThat(fixture.calls).isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : un compte qui a perdu son mot de passe entre-temps n'en reçoit pas, ni révocation")
    void auth70AccountWithoutPasswordAnymoreIsRefused() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        fixture.credentials.hashes().remove(valentin.user());

        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.InvalidToken());
        assertThat(fixture.credentials.hashes()).doesNotContainKey(valentin.user());
        assertThat(fixture.calls).doesNotContain("révocation:" + valentin.user().value());
    }

    @Test
    @DisplayName("AUTH-70 : un lien sert jusqu'à sa dernière milliseconde")
    void auth70LinkWorksUntilItsLastMillisecond() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        Instant beforeExpiry = NOW.plus(Duration.ofMinutes(30)).minusMillis(1);
        fixture.clock.advance(Duration.between(NOW, beforeExpiry));

        assertThat(handler.reset(token.value(), NEW_PASSWORD)).isEqualTo(new ResetOutcome.Done());
    }
}
