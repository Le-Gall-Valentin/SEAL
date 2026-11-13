package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.AccountLookupQuery.Contact;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountLookupHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final AccountLookupHandler handler = new AccountLookupHandler(identity.users());

    @Test
    @DisplayName("AUTH-20 : l'identifiant trouve un compte en attente comme un compte actif")
    void auth20UsernameFindsAPendingOrActiveAccount() {
        Account pending = identity.add("attente", "a@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account active = identity.add("actif", "b@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findForLogin("attente")).contains(pending.id());
        assertThat(handler.findForLogin("actif")).contains(active.id());
    }

    @Test
    @DisplayName("AUTH-20 : le courriel ne trouve qu'un compte actif (ADR 0019)")
    void auth20EmailFindsOnlyAnActiveAccount() {
        identity.add("attente", "attente@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account active = identity.add("actif", "actif@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findForLogin("attente@exemple.ca")).isEmpty();
        assertThat(handler.findForLogin("actif@exemple.ca")).contains(active.id());
    }

    @Test
    @DisplayName("AUTH-20 : un compte supprimé n'est pas trouvé, ni par identifiant ni par courriel")
    void auth20DeletedAccountIsNotFound() {
        identity.add("supprime", "supprime@exemple.ca", AccountStatus.DELETED);

        assertThat(handler.findForLogin("supprime")).isEmpty();
        assertThat(handler.findForLogin("supprime@exemple.ca")).isEmpty();
    }

    @Test
    @DisplayName("AUTH-20 : la saisie est normalisée (NFC, minuscules) avant la recherche (ADR 0021)")
    void auth20InputIsFoldedBeforeLookup() {
        Account account = identity.add("valentin.le.gall", "Val\u00e9ntin@Exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findForLogin("Valentin.Le.Gall")).contains(account.id());
        assertThat(handler.findForLogin("VAL\u00c9NTIN@EXEMPLE.CA")).contains(account.id());
        assertThat(handler.findForLogin("vale\u0301ntin@exemple.ca")).contains(account.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "pas un identifiant", "a@b", "@exemple.ca"})
    @DisplayName("AUTH-20 : une saisie qui n'est ni un identifiant ni un courriel ne trouve rien")
    void auth20MalformedInputFindsNothing(String login) {
        identity.add("valentin", "valentin@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findForLogin(login)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-20 : une saisie trop longue ou à demi-paire isolée ne trouve rien, sans erreur")
    void auth20OversizedOrIllFormedInputFindsNothing() {
        identity.add("valentin", "valentin@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findForLogin("v".repeat(300))).isEmpty();
        assertThat(handler.findForLogin("valentin" + Character.toString(0xD800)))
                .isEmpty();
    }

    @Test
    @DisplayName("AUTH-16 : seul un compte actif est vérifié ; un compte inconnu ne l'est pas")
    void auth16OnlyAnActiveAccountIsVerified() {
        Account pending = identity.add("attente", "a@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account active = identity.add("actif", "b@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.isVerified(active.id())).isTrue();
        assertThat(handler.isVerified(pending.id())).isFalse();
        assertThat(handler.isVerified(new UserId(UUID.randomUUID()))).isFalse();
    }

    @Test
    @DisplayName("AUTH-70 : le courriel normalisé donne les coordonnées du seul compte actif (ADR 0022)")
    void auth70EmailGivesTheActiveAccountContact() {
        identity.add("attente", "valentin@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account active = identity.add("valentin", "Val\u00e9ntin@Exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findActiveByEmail("VALE\u0301NTIN@EXEMPLE.CA"))
                .get()
                .extracting(Contact::user, Contact::username, Contact::email)
                .containsExactly(active.id(), "valentin", active.email().value());
    }

    @Test
    @DisplayName("AUTH-70 : un compte en attente ou supprimé n'a pas de coordonnées (ADR 0022)")
    void auth70PendingOrDeletedAccountHasNoContact() {
        Account pending = identity.add("attente", "attente@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account deleted = identity.add("supprime", "supprime@exemple.ca", AccountStatus.DELETED);

        assertThat(handler.findActiveByEmail("attente@exemple.ca")).isEmpty();
        assertThat(handler.findActiveByEmail("supprime@exemple.ca")).isEmpty();
        assertThat(handler.findActiveContact(pending.id())).isEmpty();
        assertThat(handler.findActiveContact(deleted.id())).isEmpty();
        assertThat(handler.findActiveContact(new UserId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : un compte actif donne ses coordonnées par son identifiant de compte")
    void auth70ActiveAccountGivesItsContactById() {
        Account active = identity.add("valentin", "valentin@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findActiveContact(active.id()))
                .get()
                .extracting(Contact::user, Contact::username, Contact::email)
                .containsExactly(active.id(), "valentin", "valentin@exemple.ca");
    }

    @Test
    @DisplayName("AUTH-11 : les mots personnels d'un contact sont son identifiant et la partie locale de son courriel")
    void auth11ContactGivesUsernameAndEmailLocalPart() {
        Account active = identity.add("valentin", "fjord+seal@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findActiveContact(active.id()))
                .get()
                .extracting(Contact::personalWords)
                .isEqualTo(List.of("valentin", "fjord"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "valentin", "a@b", "@exemple.ca", "valentin@exemple.ca\u0000"})
    @DisplayName("AUTH-70 : une saisie qui n'est pas un courriel ne donne rien, sans erreur")
    void auth70MalformedEmailGivesNothing(String email) {
        identity.add("valentin", "valentin@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.findActiveByEmail(email)).isEmpty();
    }
}
