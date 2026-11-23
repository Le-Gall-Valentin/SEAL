package fr.vlegall.seal.friends.infrastructure.web;

import static fr.vlegall.seal.friends.infrastructure.web.FriendsTestClient.FRIENDS;
import static fr.vlegall.seal.friends.infrastructure.web.FriendsTestClient.REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** FRD-10, FRD-11, section 14.4, ADR 0037 (point 3), QA-05 : listes d'amis et d'invitations, retrait d'un ami. */
@SealIntegrationTest
class FriendListsIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void reset() {
        mail.reset();
    }

    private FriendsTestClient account(String prefix) throws Exception {
        return FriendsTestClient.account(
                mvc,
                mail,
                prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    private UUID invite(FriendsTestClient sender, FriendsTestClient receiver) throws Exception {
        return UUID.fromString(AuthClient.body(sender.invite(receiver.username)
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString());
    }

    private void befriend(FriendsTestClient a, FriendsTestClient b) throws Exception {
        UUID request = invite(a, b);
        mvc.perform(b.unsafe(post(REQUESTS + "/" + request + "/accept"), null)).andExpect(status().isNoContent());
    }

    private ResultActions read(FriendsTestClient reader, String path) throws Exception {
        return mvc.perform(reader.safe(get(path)));
    }

    private JsonNode page(FriendsTestClient reader, String path) throws Exception {
        return AuthClient.body(read(reader, path).andExpect(status().isOk()).andReturn());
    }

    private void deactivate(FriendsTestClient account) {
        jdbc.sql("UPDATE users SET status = 'DELETED', email = NULL, deleted_at = now() WHERE id = ?")
                .param(account.id)
                .update();
    }

    @Test
    @DisplayName("Section 14.4, API-01 : reçues avec le nom affiché de l'émetteur, envoyées avec l'identifiant seul")
    void requestsAreListedByDirection() throws Exception {
        FriendsTestClient me = account("moi");
        FriendsTestClient from = account("de");
        FriendsTestClient to = account("vers");
        UUID incoming = invite(from, me);
        UUID outgoing = invite(me, to);

        JsonNode received = page(me, REQUESTS + "?direction=incoming");
        JsonNode sent = page(me, REQUESTS + "?direction=outgoing");

        assertThat(received.get("items")).hasSize(1);
        JsonNode item = received.get("items").get(0);
        assertThat(item.get("id").asString()).isEqualTo(incoming.toString());
        assertThat(item.get("userId").asString()).isEqualTo(from.id.toString());
        assertThat(item.get("username").asString()).isEqualTo(from.username);
        assertThat(item.get("displayName").asString()).isEqualTo("Valentin");
        assertThat(Instant.parse(item.get("createdAt").asString())).isNotNull();
        assertThat(received.get("next").isNull()).isTrue();
        assertThat(sent.get("items")).hasSize(1);
        assertThat(sent.get("items").get(0).get("id").asString()).isEqualTo(outgoing.toString());
        assertThat(sent.get("items").get(0).get("userId").asString()).isEqualTo(to.id.toString());
        assertThat(sent.get("items").get(0).get("username").asString()).isEqualTo(to.username);
        // API-01, ADR 0037 (point 9) : le nom affiché d'un non-ami n'est servi que s'il nous a invités.
        assertThat(sent.get("items").get(0).has("displayName")).isFalse();
    }

    @Test
    @DisplayName(
            "ADR 0037, point 3 : invitations paginées par curseur, sans doublon ni trou ; close ou inactive absente")
    void requestsArePaginated() throws Exception {
        FriendsTestClient me = account("pag");
        List<UUID> expected = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            expected.add(invite(account("pa"), me));
        }
        FriendsTestClient gone = account("parti");
        invite(gone, me);
        deactivate(gone);
        FriendsTestClient declined = account("non");
        UUID refused = invite(declined, me);
        mvc.perform(me.unsafe(post(REQUESTS + "/" + refused + "/decline"), null))
                .andExpect(status().isNoContent());

        List<String> seen = new ArrayList<>();
        String next = null;
        int pages = 0;
        do {
            JsonNode body = page(me, REQUESTS + "?direction=incoming&limit=2" + (next == null ? "" : "&after=" + next));
            body.get("items")
                    .valueStream()
                    .forEach(item -> seen.add(item.get("id").asString()));
            next = body.get("next").isNull() ? null : body.get("next").asString();
            pages++;
        } while (next != null && pages < 10);

        assertThat(seen)
                .containsExactlyInAnyOrderElementsOf(
                        expected.stream().map(UUID::toString).toList());
        assertThat(pages).isBetween(2, 3);
    }

    @Test
    @DisplayName("FRD-10 : chaque ami avec identifiant, nom, date d'amitié et statistiques, vides avant le lot 4")
    void frd10FriendsAreListedWithStatistics() throws Exception {
        FriendsTestClient me = account("ami");
        FriendsTestClient friend = account("copain");
        befriend(me, friend);

        JsonNode mine = page(me, FRIENDS);
        JsonNode theirs = page(friend, FRIENDS);

        assertThat(mine.get("items")).hasSize(1);
        JsonNode item = mine.get("items").get(0);
        assertThat(item.get("userId").asString()).isEqualTo(friend.id.toString());
        assertThat(item.get("username").asString()).isEqualTo(friend.username);
        assertThat(item.get("displayName").asString()).isEqualTo("Valentin");
        assertThat(Instant.parse(item.get("friendsSince").asString())).isNotNull();
        assertThat(item.has("lastInteractionAt")).isTrue();
        assertThat(item.get("lastInteractionAt").isNull()).isTrue();
        assertThat(item.get("interactionCount30d").intValue()).isZero();
        assertThat(mine.get("next").isNull()).isTrue();
        assertThat(theirs.get("items").get(0).get("userId").asString()).isEqualTo(me.id.toString());
        assertThat(page(me, REQUESTS + "?direction=outgoing").get("items")).isEmpty();
    }

    @Test
    @DisplayName("ADR 0037, point 3 : amis paginés par curseur ; un compte inactif disparaît de la liste")
    void friendsArePaginatedWithoutInactiveAccounts() throws Exception {
        FriendsTestClient me = account("liste");
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FriendsTestClient friend = account("li");
            befriend(friend, me);
            expected.add(friend.id.toString());
        }
        FriendsTestClient gone = account("disparu");
        befriend(gone, me);
        deactivate(gone);

        JsonNode first = page(me, FRIENDS + "?limit=2");
        JsonNode rest = page(me, FRIENDS + "?limit=2&after=" + first.get("next").asString());

        List<String> seen = new ArrayList<>();
        first.get("items")
                .valueStream()
                .forEach(item -> seen.add(item.get("userId").asString()));
        rest.get("items")
                .valueStream()
                .forEach(item -> seen.add(item.get("userId").asString()));
        if (!rest.get("next").isNull()) {
            page(me, FRIENDS + "?limit=2&after=" + rest.get("next").asString())
                    .get("items")
                    .valueStream()
                    .forEach(item -> seen.add(item.get("userId").asString()));
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("Section 14.4 : direction absente ou inconnue, limit hors de 1 à 50, after mal formé : 400")
    void invalidParametersAreRejected() throws Exception {
        FriendsTestClient me = account("param");

        for (String path : List.of(
                REQUESTS,
                REQUESTS + "?direction=both",
                REQUESTS + "?direction=incoming&limit=0",
                REQUESTS + "?direction=incoming&limit=51",
                REQUESTS + "?direction=incoming&after=pas-un-uuid",
                FRIENDS + "?limit=0",
                FRIENDS + "?limit=51",
                FRIENDS + "?after=pas-un-uuid")) {
            read(me, path)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        read(me, FRIENDS + "?limit=50").andExpect(status().isOk());
    }

    @Test
    @DisplayName("FRD-11 : retirer un ami supprime l'amitié pour les deux, journalisé ; ensuite 404")
    void frd11RemovingAFriendEndsItForBoth() throws Exception {
        FriendsTestClient me = account("retire");
        FriendsTestClient friend = account("ancien");
        befriend(me, friend);

        mvc.perform(me.unsafe(delete(FRIENDS + "/" + friend.id), null)).andExpect(status().isNoContent());

        assertThat(page(me, FRIENDS).get("items")).isEmpty();
        assertThat(page(friend, FRIENDS).get("items")).isEmpty();
        assertThat(jdbc.sql("SELECT details::text FROM security_events WHERE user_id = ? AND type = 'FRIEND_REMOVED'")
                        .param(me.id)
                        .query(String.class)
                        .single())
                .contains(friend.id.toString());
        mvc.perform(friend.unsafe(delete(FRIENDS + "/" + me.id), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        me.invite(friend.username).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FRD-11, A01 : retirer un non-ami, soi-même ou un inconnu : 404 NOT_FOUND")
    void frd11RemovingANonFriendIsNotFound() throws Exception {
        FriendsTestClient me = account("nonami");
        FriendsTestClient stranger = account("etranger");

        for (UUID target : List.of(stranger.id, me.id, UUID.randomUUID())) {
            mvc.perform(me.unsafe(delete(FRIENDS + "/" + target), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("QA-05 : listes et retrait : 401 sans session, 403 en session restreinte, 403 sans jeton CSRF")
    void qa05AccessControl() throws Exception {
        FriendsTestClient me = account("qal");
        FriendsTestClient friend = account("qaf");
        befriend(me, friend);
        AuthClient anonymous = new AuthClient(mvc, mail);
        AuthClient restricted = new AuthClient(mvc, mail);
        String pending = "qap" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AuthClient.Registered registered = restricted.register(pending + "@exemple.ca", pending);

        for (String path : List.of(FRIENDS, REQUESTS + "?direction=incoming")) {
            mvc.perform(anonymous.from(get(path))).andExpect(status().isUnauthorized());
            mvc.perform(restricted.from(get(path)).cookie(registered.session())).andExpect(status().isForbidden());
        }
        mvc.perform(anonymous.unsafe(delete(FRIENDS + "/" + friend.id), null, null))
                .andExpect(status().isUnauthorized());
        mvc.perform(me.client.from(delete(FRIENDS + "/" + friend.id)).cookie(me.session))
                .andExpect(status().isForbidden());
        mvc.perform(restricted.unsafe(delete(FRIENDS + "/" + friend.id), registered.session(), null))
                .andExpect(status().isForbidden());
        assertThat(page(me, FRIENDS).get("items")).hasSize(1);
    }

    @Test
    @DisplayName("Section 14 : une méthode hors de 14.4 est refusée par défaut (403), même en session complète")
    void unlistedMethodsAreDeniedByDefault() throws Exception {
        FriendsTestClient me = account("meth");
        FriendsTestClient friend = account("methf");
        befriend(me, friend);
        UUID request = invite(me, account("methr"));

        mvc.perform(me.unsafe(put(FRIENDS + "/" + friend.id), null)).andExpect(status().isForbidden());
        mvc.perform(me.unsafe(post(FRIENDS), null)).andExpect(status().isForbidden());
        mvc.perform(me.unsafe(put(REQUESTS + "/" + request), null)).andExpect(status().isForbidden());
        mvc.perform(me.unsafe(post(REQUESTS + "/" + request + "/autre"), null)).andExpect(status().isForbidden());
        mvc.perform(me.unsafe(patch(REQUESTS), null)).andExpect(status().isForbidden());
    }
}
