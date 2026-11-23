package fr.vlegall.seal.friends.infrastructure.web;

import static fr.vlegall.seal.friends.infrastructure.web.FriendsTestClient.REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** FRD-01 à FRD-03, section 10.1, 15.2, QA-05 : inviter par identifiant ({@code POST /api/v1/friend-requests}). */
@SealIntegrationTest
class FriendRequestSendIT {

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

    /** Identifiant neuf, au format d'AUTH-01. */
    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private FriendsTestClient account(String prefix) throws Exception {
        return FriendsTestClient.account(mvc, mail, name(prefix));
    }

    /** Compte actif sans session, inséré directement : une cible d'invitation suffit. */
    private UUID target(String username) {
        return jdbc.sql("INSERT INTO users (username, display_name, email, status) "
                        + "VALUES (?, 'Cible', CAST(? AS citext), 'ACTIVE') RETURNING id")
                .param(username)
                .param(username + "@exemple.ca")
                .query(UUID.class)
                .single();
    }

    private long pendingBetween(UUID a, UUID b) {
        return jdbc.sql("SELECT count(*) FROM friend_requests WHERE status = 'PENDING' AND "
                        + "((sender_id = :a AND receiver_id = :b) OR (sender_id = :b AND receiver_id = :a))")
                .param("a", a)
                .param("b", b)
                .query(Long.class)
                .single();
    }

    private void befriend(UUID a, UUID b) {
        jdbc.sql("INSERT INTO friendships (user_low, user_high) VALUES (LEAST(:a, :b), GREATEST(:a, :b))")
                .param("a", a)
                .param("b", b)
                .update();
    }

    @Test
    @DisplayName("FRD-01 : invitation créée, 201 avec l'invitation et le profil du destinataire")
    void frd01InvitationIsCreated() throws Exception {
        FriendsTestClient sender = account("inv");
        FriendsTestClient receiver = account("dest");

        MvcResult result = sender.invite(receiver.username)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(receiver.id.toString()))
                .andExpect(jsonPath("$.username").value(receiver.username))
                .andExpect(jsonPath("$.displayName").value("Valentin"))
                .andReturn();

        UUID id = UUID.fromString(AuthClient.body(result).get("id").asString());
        assertThat(Instant.parse(AuthClient.body(result).get("createdAt").asString()))
                .isNotNull();
        assertThat(jdbc.sql("SELECT sender_id FROM friend_requests WHERE id = ? AND status = 'PENDING'")
                        .param(id)
                        .query(UUID.class)
                        .single())
                .isEqualTo(sender.id);
    }

    @Test
    @DisplayName("ADR 0037, point 6 : espaces, @ initial et majuscules sont retirés avant la recherche")
    void adr0037InputIsNormalized() throws Exception {
        FriendsTestClient sender = account("norm");
        String receiver = name("sarah.b");
        target(receiver);

        sender.invite("  @" + receiver.toUpperCase(java.util.Locale.ROOT) + " ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(receiver));
    }

    @Test
    @DisplayName("ADR 0037, point 6 : une saisie hors du format d'AUTH-01 est refusée, 400 VALIDATION_FAILED")
    void adr0037MalformedInputIsRejected() throws Exception {
        FriendsTestClient sender = account("mal");

        for (String input : List.of("ab", "deux mots", "@", "é" + name("x"), "a".repeat(21))) {
            sender.invite(input)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        sender.invite("a".repeat(65)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FRD-02 : identifiant inexistant, en attente ou supprimé : 404 USER_NOT_FOUND")
    void frd02UnknownOrInactiveUserIsNotFound() throws Exception {
        FriendsTestClient sender = account("enum");
        String deleted = name("supp");
        jdbc.sql("INSERT INTO users (username, display_name, status, deleted_at) VALUES (?, 'Nom', 'DELETED', now())")
                .param(deleted)
                .update();
        AuthClient pendingClient = new AuthClient(mvc, mail);
        String pending = name("att");
        pendingClient.register(pending + "@exemple.ca", pending);

        for (String username : List.of(name("absent"), deleted, pending)) {
            sender.invite(username)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("Section 10.1 : s'inviter soi-même, 422 CANNOT_INVITE_SELF")
    void selfInvitationIsRejected() throws Exception {
        FriendsTestClient sender = account("soi");

        sender.invite("@" + sender.username)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_INVITE_SELF"));
    }

    @Test
    @DisplayName("FRD-03 : blocage dans un sens, dans l'autre, ou invitations fermées : le même 403 CANNOT_INVITE")
    void frd03SameRefusalForEveryCause() throws Exception {
        FriendsTestClient sender = account("bloq");
        UUID blockedBySender = target(name("b1"));
        UUID blockingSender = target(name("b2"));
        UUID closed = target(name("b3"));
        jdbc.sql("INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?), (?, ?)")
                .param(sender.id)
                .param(blockedBySender)
                .param(blockingSender)
                .param(sender.id)
                .update();
        jdbc.sql("INSERT INTO user_settings (user_id, invitation_policy) VALUES (?, 'NOBODY')")
                .param(closed)
                .update();

        List<String> bodies = new java.util.ArrayList<>();
        for (UUID target : List.of(blockedBySender, blockingSender, closed)) {
            String username = jdbc.sql("SELECT username FROM users WHERE id = ?")
                    .param(target)
                    .query(String.class)
                    .single();
            bodies.add(sender.invite(username)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CANNOT_INVITE"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            assertThat(pendingBetween(sender.id, target)).isZero();
        }
        assertThat(bodies).containsOnly(bodies.getFirst());
    }

    @Test
    @DisplayName("Section 10.1 : déjà amis, 409 ALREADY_FRIENDS")
    void alreadyFriendsIsRejected() throws Exception {
        FriendsTestClient sender = account("ami");
        String friend = name("deja");
        befriend(sender.id, target(friend));

        sender.invite(friend)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
    }

    @Test
    @DisplayName("FRD-01 : seconde invitation dans le même sens, 409 REQUEST_ALREADY_PENDING")
    void frd01SecondInvitationIsAlreadyPending() throws Exception {
        FriendsTestClient sender = account("deux");
        String receiver = name("recoit");
        UUID receiverId = target(receiver);
        sender.invite(receiver).andExpect(status().isCreated());

        sender.invite(receiver)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_PENDING"));
        assertThat(pendingBetween(sender.id, receiverId)).isEqualTo(1);
    }

    @Test
    @DisplayName("FRD-01 : l'autre a déjà invité, 409 INCOMING_REQUEST_EXISTS avec requestId")
    void frd01IncomingRequestIsReported() throws Exception {
        FriendsTestClient first = account("prem");
        FriendsTestClient second = account("sec");
        String requestId = AuthClient.body(first.invite(second.username)
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString();

        second.invite(first.username)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOMING_REQUEST_EXISTS"))
                .andExpect(jsonPath("$.requestId").value(requestId));
        assertThat(pendingBetween(first.id, second.id)).isEqualTo(1);
    }

    @Test
    @DisplayName("FRD-01 : invitations croisées simultanées : une seule en attente, l'autre 409, jamais 500")
    void frd01CrossedInvitationsKeepOnePending() throws Exception {
        for (int round = 0; round < 3; round++) {
            FriendsTestClient a = account("cra");
            FriendsTestClient b = account("crb");

            List<Integer> statuses = Race.run(
                    2,
                    index -> (index == 0 ? a.invite(b.username) : b.invite(a.username))
                            .andReturn()
                            .getResponse()
                            .getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(pendingBetween(a.id, b.id)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("FRD-01 : après un retrait, l'ancienne invitation acceptée n'empêche pas d'inviter à nouveau")
    void frd01InviteAgainAfterRemoval() throws Exception {
        FriendsTestClient sender = account("rev");
        String former = name("ancien");
        UUID formerId = target(former);
        jdbc.sql("INSERT INTO friend_requests (sender_id, receiver_id, status, responded_at) "
                        + "VALUES (?, ?, 'ACCEPTED', now())")
                .param(formerId)
                .param(sender.id)
                .update();

        sender.invite(former).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FRD-02, SEC-20 : l'invitation créée est journalisée avec des identifiants internes seulement")
    void frd02SentInvitationIsJournaled() throws Exception {
        FriendsTestClient sender = account("jour");
        String receiver = name("lu");
        UUID receiverId = target(receiver);

        String id =
                AuthClient.body(sender.invite(receiver).andReturn()).get("id").asString();

        String details = jdbc.sql(
                        "SELECT details::text FROM security_events WHERE user_id = ? AND type = 'FRIEND_REQUEST_SENT'")
                .param(sender.id)
                .query(String.class)
                .single();
        assertThat(details).contains(receiverId.toString(), id).doesNotContain(receiver);
    }

    @Test
    @DisplayName("FRD-02, 15.2 : 60 tentatives par heure, identifiants inexistants compris, puis 429 avec Retry-After")
    void frd02SixtyAttemptsPerHour() throws Exception {
        FriendsTestClient sender = account("tent");

        for (int i = 0; i < 60; i++) {
            sender.invite(name("nul")).andExpect(status().isNotFound());
        }

        sender.invite(name("nul"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("FRD-02, 15.2 : 20 invitations créées par heure ; un refus ne compte pas parmi elles")
    void frd02TwentyCreatedPerHour() throws Exception {
        FriendsTestClient sender = account("cree");
        for (int i = 0; i < 25; i++) {
            sender.invite(name("nul")).andExpect(status().isNotFound());
        }

        for (int i = 0; i < 20; i++) {
            sender.invite(jdbcTarget()).andExpect(status().isCreated());
        }

        sender.invite(jdbcTarget())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    private String jdbcTarget() {
        String username = name("cib");
        target(username);
        return username;
    }

    @Test
    @DisplayName("QA-05 : 401 sans session, 403 sans jeton CSRF, 403 en session restreinte")
    void qa05AccessControl() throws Exception {
        FriendsTestClient sender = account("qa");
        AuthClient anonymous = new AuthClient(mvc, mail);
        Map<String, Object> body = Map.of("username", sender.username);

        mvc.perform(anonymous.unsafe(post(REQUESTS), null, body)).andExpect(status().isUnauthorized());
        mvc.perform(sender.client
                        .from(post(REQUESTS))
                        .cookie(sender.session)
                        .contentType("application/json")
                        .content("{\"username\":\"" + sender.username + "\"}"))
                .andExpect(status().isForbidden());
        AuthClient restricted = new AuthClient(mvc, mail);
        String pending = name("restr");
        AuthClient.Registered registered = restricted.register(pending + "@exemple.ca", pending);
        mvc.perform(restricted.unsafe(post(REQUESTS), registered.session(), body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("QA-05, SEC-01 : champ inconnu ou identifiant absent, 400 VALIDATION_FAILED")
    void qa05InvalidBodies() throws Exception {
        FriendsTestClient sender = account("corps");
        Map<String, Object> extra = new HashMap<>();
        extra.put("username", sender.username);
        extra.put("receiverId", UUID.randomUUID().toString());

        mvc.perform(sender.unsafe(post(REQUESTS), extra))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(sender.unsafe(post(REQUESTS), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
