package fr.vlegall.seal.friends.infrastructure.web;

import static fr.vlegall.seal.friends.infrastructure.web.FriendsTestClient.REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import fr.vlegall.seal.support.TestRelations;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
        return FriendsTestClient.fresh(mvc, mail, prefix);
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
    @DisplayName("FRD-01, section 10.1 : le destinataire accepte ; l'amitié naît dans la même transaction, journalisée")
    void frd01AcceptCreatesTheFriendship() throws Exception {
        FriendsTestClient sender = account("acc");
        FriendsTestClient receiver = account("rec");
        UUID request = sender.inviteOk(receiver);

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
    @DisplayName("FRD-01, section 10.1 : le destinataire refuse, sans amitié ; l'émetteur peut réinviter")
    void frd01DeclineClosesWithoutFriendship() throws Exception {
        FriendsTestClient sender = account("ref");
        FriendsTestClient receiver = account("rer");
        UUID request = sender.inviteOk(receiver);

        as(receiver, decline(request)).andExpect(status().isNoContent());

        assertThat(statusOf(request)).isEqualTo("DECLINED");
        assertThat(friendships(sender.id, receiver.id)).isZero();
        sender.invite(receiver.username).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FRD-01, section 10.1 : l'émetteur révoque son invitation")
    void frd01SenderRevokes() throws Exception {
        FriendsTestClient sender = account("rvk");
        FriendsTestClient receiver = account("rvr");
        UUID request = sender.inviteOk(receiver);

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
        UUID request = sender.inviteOk(receiver);

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
            UUID request = sender.inviteOk(receiver);

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
    @DisplayName("FRD-01, section 10.1 : acceptation et révocation simultanées : une seule l'emporte, état cohérent")
    void frd01AcceptAndRevokeRaceLeavesAConsistentState() throws Exception {
        for (int round = 0; round < 3; round++) {
            FriendsTestClient sender = account("cor");
            FriendsTestClient receiver = account("crr");
            UUID request = sender.inviteOk(receiver);

            List<Integer> statuses = Race.run(
                    2,
                    index -> (index == 0 ? as(receiver, accept(request)) : as(sender, revoke(request)))
                            .andReturn()
                            .getResponse()
                            .getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(204, 404);
            boolean accepted = statuses.get(0) == 204;
            assertThat(statusOf(request)).isEqualTo(accepted ? "ACCEPTED" : "REVOKED");
            assertThat(friendships(sender.id, receiver.id)).isEqualTo(accepted ? 1 : 0);
        }
    }

    @Test
    @DisplayName("FRD-01 : réinviter pendant que l'autre accepte ne laisse jamais d'invitation en attente entre amis")
    void frd01InvitingWhileAcceptingNeverLeavesAPendingRequestBetweenFriends() throws Exception {
        for (int round = 0; round < 5; round++) {
            FriendsTestClient sender = account("rea");
            FriendsTestClient receiver = account("rer");
            UUID request = sender.inviteOk(receiver);

            List<MvcResult> results = Race.run(
                    2,
                    index -> (index == 0 ? as(receiver, accept(request)) : sender.invite(receiver.username))
                            .andReturn());

            assertThat(results.get(0).getResponse().getStatus()).isEqualTo(204);
            assertThat(results.get(1).getResponse().getStatus()).isEqualTo(409);
            assertThat(AuthClient.body(results.get(1)).get("code").asString())
                    .isIn("REQUEST_ALREADY_PENDING", "ALREADY_FRIENDS");

            long pending = jdbc.sql("SELECT count(*) FROM friend_requests WHERE status = 'PENDING'"
                            + " AND sender_id IN (:a, :b) AND receiver_id IN (:a, :b)")
                    .param("a", sender.id)
                    .param("b", receiver.id)
                    .query(Long.class)
                    .single();
            assertThat(friendships(sender.id, receiver.id)).isEqualTo(1);
            assertThat(pending).isZero();
        }
    }

    @Test
    @DisplayName("ADR 0037 : l'invitation d'un compte qui n'est plus actif ne s'accepte ni ne se refuse plus")
    void adr0037InactiveSenderInvitationIsGone() throws Exception {
        FriendsTestClient sender = account("ina");
        FriendsTestClient receiver = account("inr");
        UUID request = sender.inviteOk(receiver);
        TestRelations.deactivate(jdbc, sender.id);

        assertNotFound(as(receiver, accept(request)));
        assertNotFound(as(receiver, decline(request)));
        assertThat(friendships(sender.id, receiver.id)).isZero();
    }

    @Test
    @DisplayName("ADR 0037, point 7 : révoquer une invitation dont le destinataire n'est plus actif, 404")
    void adr0037RevokingWhenTheReceiverIsInactiveIsNotFound() throws Exception {
        FriendsTestClient sender = account("rvi");
        FriendsTestClient receiver = account("rvd");
        UUID request = sender.inviteOk(receiver);
        TestRelations.deactivate(jdbc, receiver.id);

        assertNotFound(as(sender, revoke(request)));
        assertThat(statusOf(request)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("FRD-01, section 10.1 : une invitation révoquée disparaît des deux listes")
    void frd01RevokedInvitationLeavesBothLists() throws Exception {
        FriendsTestClient sender = account("rvl");
        FriendsTestClient receiver = account("rvm");
        UUID request = sender.inviteOk(receiver);

        as(sender, revoke(request)).andExpect(status().isNoContent());

        for (MockHttpServletRequestBuilder list : List.of(
                sender.safe(get(REQUESTS + "?direction=outgoing")),
                receiver.safe(get(REQUESTS + "?direction=incoming")))) {
            assertThat(AuthClient.body(
                                    mvc.perform(list).andExpect(status().isOk()).andReturn())
                            .get("items"))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("SEC-01 : un identifiant d'invitation ou d'ami qui n'est pas un UUID, 400 VALIDATION_FAILED")
    void sec01MalformedPathIdentifiersAreRejected() throws Exception {
        FriendsTestClient actor = account("pth");

        for (MockHttpServletRequestBuilder route : List.of(
                post(REQUESTS + "/pas-un-uuid/accept"),
                post(REQUESTS + "/pas-un-uuid/decline"),
                delete(REQUESTS + "/pas-un-uuid"),
                delete(FriendsTestClient.FRIENDS + "/pas-un-uuid"))) {
            as(actor, route)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @DisplayName("QA-05 : 401 sans session, 403 sans jeton CSRF, 403 en session restreinte, pour les trois routes")
    void qa05AccessControl() throws Exception {
        FriendsTestClient sender = account("qas");
        FriendsTestClient receiver = account("qar");
        UUID request = sender.inviteOk(receiver);
        AuthClient anonymous = new AuthClient(mvc, mail);
        AuthClient restricted = new AuthClient(mvc, mail);
        String pending = FriendsTestClient.name("qat");
        AuthClient.Registered registered = restricted.register(pending + "@exemple.ca", pending);

        for (MockHttpServletRequestBuilder route : List.of(accept(request), decline(request), revoke(request))) {
            mvc.perform(anonymous.unsafe(route, null, null))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        for (MockHttpServletRequestBuilder route : List.of(accept(request), decline(request), revoke(request))) {
            mvc.perform(receiver.client.from(route).cookie(receiver.session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        for (MockHttpServletRequestBuilder route : List.of(accept(request), decline(request), revoke(request))) {
            mvc.perform(restricted.unsafe(route, registered.session(), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(statusOf(request)).isEqualTo("PENDING");
    }
}
