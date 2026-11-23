package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.UserDirectoryQuery.Profile;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.InvitationPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FRD-02, API-01, ADR 0037 (point 8) : annuaire interne demandé par le contexte {@code friends} ; il ne trouve que les
 * comptes actifs et ne livre ni courriel ni statut.
 */
@DisplayName("FRD-02 : annuaire interne des comptes actifs")
class UserDirectoryHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final UserDirectoryHandler handler = new UserDirectoryHandler(identity.users(), identity.settings());

    @Test
    @DisplayName("FRD-02 : un identifiant exact donne le profil du compte actif")
    void frd02ActiveAccountIsFoundByUsername() {
        Account sarah = identity.add("sarah.belanger", "sarah@exemple.ca", AccountStatus.ACTIVE);

        Profile profile = handler.findActiveByUsername("sarah.belanger").orElseThrow();

        assertThat(profile.user()).isEqualTo(sarah.id());
        assertThat(profile.username()).isEqualTo("sarah.belanger");
        assertThat(profile.displayName()).isEqualTo("Nom");
    }

    @Test
    @DisplayName("FRD-02 : un compte en attente, supprimé, inconnu ou un identifiant mal formé ne donne rien")
    void frd02InactiveUnknownOrMalformedGivesNothing() {
        identity.add("attente", "attente@exemple.ca", AccountStatus.PENDING_EMAIL);
        identity.add("supprime", "supprime@exemple.ca", AccountStatus.DELETED);

        assertThat(handler.findActiveByUsername("attente")).isEmpty();
        assertThat(handler.findActiveByUsername("supprime")).isEmpty();
        assertThat(handler.findActiveByUsername("inconnu")).isEmpty();
        assertThat(handler.findActiveByUsername("Mal Formé")).isEmpty();
    }

    @Test
    @DisplayName("FRD-10 : les profils suivent l'ordre de la demande, sans les comptes inactifs ou inconnus")
    void frd10ProfilesKeepTheRequestOrderWithoutInactiveAccounts() {
        Account zoe = identity.add("zoe", "zoe@exemple.ca", AccountStatus.ACTIVE);
        Account pending = identity.add("attente", "attente@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account adam = identity.add("adam", "adam@exemple.ca", AccountStatus.ACTIVE);
        Account deleted = identity.add("supprime", "supprime@exemple.ca", AccountStatus.DELETED);
        UserId unknown = new UserId(UUID.randomUUID());

        List<Profile> profiles =
                handler.activeProfiles(List.of(adam.id(), pending.id(), unknown, zoe.id(), deleted.id()));

        assertThat(profiles).extracting(Profile::user).containsExactly(adam.id(), zoe.id());
    }

    @Test
    @DisplayName("FRD-03 : la politique NOBODY ferme les invitations ; sans réglage enregistré, elles sont ouvertes")
    void frd03NobodyClosesInvitationsAndDefaultIsOpen() {
        Account open = identity.add("ouvert", "ouvert@exemple.ca", AccountStatus.ACTIVE);
        Account closed = identity.add("ferme", "ferme@exemple.ca", AccountStatus.ACTIVE);
        identity.policies.put(closed.id(), InvitationPolicy.NOBODY);

        assertThat(handler.acceptsInvitations(open.id())).isTrue();
        assertThat(handler.acceptsInvitations(closed.id())).isFalse();
    }
}
