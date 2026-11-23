package fr.vlegall.seal.friends.infrastructure.web;

import static fr.vlegall.seal.friends.infrastructure.web.FriendsTestClient.REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Section 10.1, FRD-01, QA-05 : accepter, refuser et révoquer une invitation. */
@SealIntegrationTest
class FriendRequestTransitionsIT {

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

    private static MockHttpServletRequestBuilder accept(UUID id) {
        return post(REQUESTS + "/" + id + "/accept");
    }

    private static MockHttpServletRequestBuilder decline(UUID id) {
        return post(REQUESTS + "/" + id + "/decline");
    }

    private static MockHttpServletRequestBuilder revoke(UUID id) {
        return delete(REQUESTS + "/" + id);
    }

    private ResultActions as(FriendsTestClient actor, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(actor.unsafe(request, null));
    }

    private UUID invite(FriendsTestClient sender, FriendsTestClient receiver) throws Exception {
        return UUID.fromString(AuthClient.body(sender.invite(receiver.username)
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString());
    }

    private String statusOf(UUID request) {
        return jdbc.sql("SELECT status FROM friend_requests WHERE id = ?")
                .param(request)
                .query(String.class)
                .single();
    }

    private long friendships(UUID a, UUID b) {
        return jdbc.sql(
                        "SELECT count(*) FROM friendships WHERE user_low = LEAST(:a, :b) AND user_high = GREATEST(:a, :b)")
                .param("a", a)
                .param("b", b)
                .query(Long.class)
                .single();
    }

    private void assertNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Section 10.1 : le destinataire accepte ; l'amitié naît dans la même transaction, journalisée")
    void acceptCreatesTheFriendship() throws Exception {
        FriendsTestClient sender = account("acc");
        FriendsTestClient receiver = account("rec");
        UUID request = invite(sender, receiver);

        as(receiver, accept(request)).andExpect(status().isNoContent());

        assertThat(statusOf(request)).isEqualTo("ACCEPTED");
        assertThat(friendships(sender.id, receiver.id)).isEqualTo(1);
        assertThat(jdbc.sql(
                                "SELECT details::text FROM security_events WHERE user_id = ? AND type = 'FRIEND_REQUEST_ACCEPTED'")
                        .param(receiver.id)
                        .query(String.class)
                        .single())
                .contains(sender.id.toString(), request.toString());
        sender.invite(receiver.username).andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
    }

    @Test
    @DisplayName("Section 10.1 : le destinataire refuse, sans amitié ; l'émetteur peut réinviter")
    void declineClosesWithoutFriendship() throws Exception {
        FriendsTestClient sender = account("ref");
        FriendsTestClient receiver = account("rer");
        UUID request = invite(sender, receiver);

        as(receiver, decline(request)).andExpect(status().isNoContent());

        assertThat(statusOf(request)).isEqualTo("DECLINED");
        assertThat(friendships(sender.id, receiver.id)).isZero();
        sender.invite(receiver.username).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Section 10.1 : l'émetteur révoque son invitation")
    void senderRevokes() throws Exception {
        FriendsTestClient sender = account("rvk");
        FriendsTestClient receiver = account("rvr");
        UUID request = invite(sender, receiver);

        as(sender, revoke(request)).andExpect(status().isNoContent());

        assertThat(statusOf(request)).isEqualTo("REVOKED");
        assertThat(friendships(sender.id, receiver.id)).isZero();
    }

    @Test
    @DisplayName("A01 : le mauvais acteur, un tiers, une invitation inconnue ou close reçoivent 404 NOT_FOUND")
    void a01WrongActorGetsNotFound() throws Exception {
        FriendsTestClient sender = account("mau");
        FriendsTestClient receiver = account("mar");
        FriendsTestClient stranger = account("tie");
        UUID request = invite(sender, receiver);

        assertNotFound(as(sender, accept(request)));
        assertNotFound(as(sender, decline(request)));
        assertNotFound(as(receiver, revoke(request)));
        for (MockHttpServletRequestBuilder attempt : List.of(accept(request), decline(request), revoke(request))) {
            assertNotFound(as(stranger, attempt));
        }
        assertNotFound(as(receiver, accept(UUID.randomUUID())));
        assertThat(statusOf(request)).isEqualTo("PENDING");

        as(receiver, accept(request)).andExpect(status().isNoContent());
        assertNotFound(as(receiver, accept(request)));
        assertNotFound(as(receiver, decline(request)));
        assertNotFound(as(sender, revoke(request)));
    }

    @Test
    @DisplayName("FRD-01 : deux acceptations simultanées créent une seule amitié ; la seconde reçoit 404, jamais 500")
    void frd01ConcurrentAcceptsMakeOneFriendship() throws Exception {
        for (int round = 0; round < 3; round++) {
            FriendsTestClient sender = account("dbl");
            FriendsTestClient receiver = account("dbr");
            UUID request = invite(sender, receiver);

            List<Integer> statuses = Race.run(
                    2,
                    index -> as(receiver, accept(request))
                            .andReturn()
                            .getResponse()
                            .getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(204, 404);
            assertThat(friendships(sender.id, receiver.id)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("ADR 0037 : l'invitation d'un compte qui n'est plus actif ne s'accepte ni ne se refuse plus")
    void inactiveSenderInvitationIsGone() throws Exception {
        FriendsTestClient sender = account("ina");
        FriendsTestClient receiver = account("inr");
        UUID request = invite(sender, receiver);
        jdbc.sql("UPDATE users SET status = 'DELETED', email = NULL, deleted_at = now() WHERE id = ?")
                .param(sender.id)
                .update();

        assertNotFound(as(receiver, accept(request)));
        assertNotFound(as(receiver, decline(request)));
        assertThat(friendships(sender.id, receiver.id)).isZero();
    }

    @Test
    @DisplayName("QA-05 : 401 sans session, 403 sans jeton CSRF, 403 en session restreinte, pour les trois routes")
    void qa05AccessControl() throws Exception {
        FriendsTestClient sender = account("qas");
        FriendsTestClient receiver = account("qar");
        UUID request = invite(sender, receiver);
        AuthClient anonymous = new AuthClient(mvc, mail);
        AuthClient restricted = new AuthClient(mvc, mail);
        String pending = "qat" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AuthClient.Registered registered = restricted.register(pending + "@exemple.ca", pending);

        for (MockHttpServletRequestBuilder route : List.of(accept(request), decline(request), revoke(request))) {
            mvc.perform(anonymous.unsafe(route, null, null)).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder route : List.of(accept(request), decline(request), revoke(request))) {
            mvc.perform(receiver.client.from(route).cookie(receiver.session)).andExpect(status().isForbidden());
        }
        for (MockHttpServletRequestBuilder route : List.of(accept(request), decline(request), revoke(request))) {
            mvc.perform(restricted.unsafe(route, registered.session(), null)).andExpect(status().isForbidden());
        }
        assertThat(statusOf(request)).isEqualTo("PENDING");
    }
}
