package fr.vlegall.seal.conversations.infrastructure.web;

import static fr.vlegall.seal.conversations.infrastructure.web.ConversationsTestClient.CONVERSATIONS;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** CNV-01, CNV-03, CNV-04, CNV-06, GRP-01 à GRP-03, FRD-21, SEC-01, QA-05 : routes des conversations (ADR 0039). */
@SealIntegrationTest
class ConversationsIT {

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

    private ConversationsTestClient account(String prefix) throws Exception {
        return ConversationsTestClient.fresh(mvc, mail, prefix);
    }

    private ResultActions direct(ConversationsTestClient caller, UUID other) throws Exception {
        return mvc.perform(caller.unsafe(post(CONVERSATIONS + "/direct"), Map.of("userId", other)));
    }

    private ResultActions group(ConversationsTestClient caller, String name, List<UUID> members) throws Exception {
        return mvc.perform(caller.unsafe(post(CONVERSATIONS + "/group"), Map.of("name", name, "memberIds", members)));
    }

    private String groupOk(ConversationsTestClient caller, List<UUID> members) throws Exception {
        return AuthClient.body(group(caller, "Ski du dimanche", members)
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString();
    }

    private JsonNode read(ConversationsTestClient caller, String path) throws Exception {
        return AuthClient.body(
                mvc.perform(caller.safe(get(path))).andExpect(status().isOk()).andReturn());
    }

    @Test
    @DisplayName("CNV-01 : 201 et created true à la création, puis 200 et created false, dans l'autre sens")
    void cnv01DirectIsCreatedThenFound() throws Exception {
        ConversationsTestClient me = account("cnvd");
        ConversationsTestClient friend = account("cnvf");
        ConversationsTestClient.befriend(jdbc, me, friend);

        JsonNode created = AuthClient.body(
                direct(me, friend.id).andExpect(status().isCreated()).andReturn());
        JsonNode found =
                AuthClient.body(direct(friend, me.id).andExpect(status().isOk()).andReturn());

        assertThat(created.get("created").asBoolean()).isTrue();
        assertThat(found.get("created").asBoolean()).isFalse();
        JsonNode conversation = created.get("conversation");
        assertThat(found.get("conversation").get("id").asString())
                .isEqualTo(conversation.get("id").asString());
        assertThat(conversation.get("type").asString()).isEqualTo("DIRECT");
        assertThat(conversation.get("name").isNull()).isTrue();
        assertThat(conversation.get("members")).hasSize(2);
        assertThat(conversation.get("lastServerSeq").asLong()).isZero();
        assertThat(conversation.get("unreadCount").asLong()).isZero();
        assertThat(conversation.get("lastSenderSeq").isNull()).isTrue();
        assertThat(conversation.get("lastEnvelope").isNull()).isTrue();
        assertThat(conversation.get("createdAt").asString()).endsWith("Z");
    }

    @Test
    @DisplayName(
            "FRD-21, ADR 0039 (point 2) : non-ami, soi-même, ancien ami : 403 NOT_FRIENDS ; la directe reste lisible")
    void frd21NonFriendsAreRefusedAlike() throws Exception {
        ConversationsTestClient me = account("frdm");
        ConversationsTestClient friend = account("frdf");
        ConversationsTestClient.befriend(jdbc, me, friend);
        String id = AuthClient.body(direct(me, friend.id).andReturn())
                .get("conversation")
                .get("id")
                .asString();
        jdbc.sql("DELETE FROM friendships WHERE ? IN (user_low, user_high)")
                .param(me.id)
                .update();

        for (UUID other : List.of(friend.id, me.id, account("frds").id, UUID.randomUUID())) {
            direct(me, other)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
        }
        assertThat(read(me, CONVERSATIONS + "/" + id).get("id").asString()).isEqualTo(id);
    }

    @Test
    @DisplayName(
            "CNV-03, GRP-02 : groupe d'amis créé (201) ; un non-ami, un doublon, un nom ou une liste hors bornes refusés")
    void cnv03GroupCreationAndRefusals() throws Exception {
        ConversationsTestClient me = account("grpm");
        ConversationsTestClient a = account("grpa");
        ConversationsTestClient b = account("grpb");
        ConversationsTestClient.befriend(jdbc, me, a);
        ConversationsTestClient.befriend(jdbc, me, b);

        JsonNode created = AuthClient.body(group(me, "Ski du dimanche", List.of(a.id, b.id))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(created.get("type").asString()).isEqualTo("GROUP");
        assertThat(created.get("name").asString()).isEqualTo("Ski du dimanche");
        assertThat(created.get("members")).hasSize(3);
        List<String> admins = new ArrayList<>();
        created.get("members").forEach(m -> {
            if ("ADMIN".equals(m.get("role").asString())) {
                admins.add(m.get("userId").asString());
            }
        });
        assertThat(admins).containsExactly(me.id.toString());

        group(me, "Ski", List.of(a.id, account("grps").id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
        group(me, "Ski", List.of(a.id, a.id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        group(me, "x".repeat(65), List.of(a.id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        group(me, "Ski", List.of()).andExpect(status().isBadRequest());
        java.util.Map<String, Object> nullList = new java.util.HashMap<>();
        nullList.put("name", "Ski");
        nullList.put("memberIds", null);
        mvc.perform(me.unsafe(post(CONVERSATIONS + "/group"), nullList))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        java.util.List<UUID> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        mvc.perform(me.unsafe(post(CONVERSATIONS + "/group"), Map.of("name", "Ski", "memberIds", withNull)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        List<UUID> fifty = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            fifty.add(UUID.randomUUID());
        }
        group(me, "Ski", fifty).andExpect(status().isBadRequest());
        mvc.perform(me.unsafe(
                        post(CONVERSATIONS + "/group"), Map.of("name", "Ski", "memberIds", List.of(a.id), "x", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName(
            "GRP-03 : ajout par un membre, droits de l'administrateur, NOT_A_MEMBER, renommage, départ et successeur")
    void grp03GroupActions() throws Exception {
        ConversationsTestClient admin = account("grad");
        ConversationsTestClient member = account("grme");
        ConversationsTestClient newcomer = account("grne");
        ConversationsTestClient.befriend(jdbc, admin, member);
        ConversationsTestClient.befriend(jdbc, member, newcomer);
        String id = groupOk(admin, List.of(member.id));
        String path = CONVERSATIONS + "/" + id;

        mvc.perform(member.unsafe(post(path + "/members"), Map.of("userIds", List.of(newcomer.id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3));
        mvc.perform(member.unsafe(delete(path + "/members/" + newcomer.id), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(admin.unsafe(delete(path + "/members/" + UUID.randomUUID()), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        mvc.perform(admin.unsafe(delete(path + "/members/" + newcomer.id), null))
                .andExpect(status().isNoContent());
        mvc.perform(admin.unsafe(patch(path), Map.of("name", "Autre nom")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Autre nom"));
        mvc.perform(admin.unsafe(post(path + "/leave"), null)).andExpect(status().isNoContent());

        JsonNode after = read(member, path);
        assertThat(after.get("members")).hasSize(1);
        assertThat(after.get("members").get(0).get("role").asString()).isEqualTo("ADMIN");
        mvc.perform(admin.safe(get(path))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
            "CNV-06, ADR 0039 (point 3) : un non-membre reçoit 404 partout ; une directe refuse les actions de groupe")
    void cnv06NonMembersGetNotFoundAndDirectRefusesGroupActions() throws Exception {
        ConversationsTestClient admin = account("nmad");
        ConversationsTestClient member = account("nmme");
        ConversationsTestClient stranger = account("nmst");
        ConversationsTestClient.befriend(jdbc, admin, member);
        String path = CONVERSATIONS + "/" + groupOk(admin, List.of(member.id));

        for (ResultActions result : List.of(
                mvc.perform(stranger.safe(get(path))),
                mvc.perform(stranger.unsafe(patch(path), Map.of("name", "Autre"))),
                mvc.perform(stranger.unsafe(post(path + "/members"), Map.of("userIds", List.of(member.id)))),
                mvc.perform(stranger.unsafe(delete(path + "/members/" + member.id), null)),
                mvc.perform(stranger.unsafe(post(path + "/leave"), null)),
                mvc.perform(admin.safe(get(CONVERSATIONS + "/" + UUID.randomUUID()))))) {
            result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        String pair = CONVERSATIONS + "/"
                + AuthClient.body(direct(admin, member.id).andReturn())
                        .get("conversation")
                        .get("id")
                        .asString();
        mvc.perform(admin.unsafe(post(pair + "/leave"), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(admin.unsafe(patch(pair), Map.of("name", "Autre"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CNV-04 : liste paginée par identifiant ; limit hors de 1 à 50 ou after mal formé : 400")
    void cnv04ListIsPaginated() throws Exception {
        ConversationsTestClient me = account("lstm");
        ConversationsTestClient friend = account("lstf");
        ConversationsTestClient.befriend(jdbc, me, friend);
        String first = groupOk(me, List.of(friend.id));
        String second = groupOk(me, List.of(friend.id));

        JsonNode page = read(me, CONVERSATIONS + "?limit=1");
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("items").get(0).get("id").asString()).isEqualTo(first);
        JsonNode next =
                read(me, CONVERSATIONS + "?limit=1&after=" + page.get("next").asString());
        assertThat(next.get("items").get(0).get("id").asString()).isEqualTo(second);
        assertThat(next.get("next").isNull()).isTrue();
        for (String query : List.of("?limit=0", "?limit=51", "?after=pas-un-uuid")) {
            mvc.perform(me.safe(get(CONVERSATIONS + query)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @DisplayName(
            "QA-05 : 401 sans session, 403 sans jeton CSRF ; méthode hors de 14.5 et historique refusés par défaut")
    void qa05AccessControl() throws Exception {
        ConversationsTestClient me = account("qacm");
        ConversationsTestClient friend = account("qacf");
        ConversationsTestClient.befriend(jdbc, me, friend);
        String path = CONVERSATIONS + "/" + groupOk(me, List.of(friend.id));
        AuthClient anonymous = new AuthClient(mvc, mail);

        mvc.perform(anonymous.from(get(CONVERSATIONS)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(anonymous.unsafe(post(CONVERSATIONS + "/direct"), null, Map.of("userId", friend.id)))
                .andExpect(status().isUnauthorized());
        mvc.perform(me.client.from(post(CONVERSATIONS + "/direct")).cookie(me.session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(me.client.from(post(path + "/leave")).cookie(me.session)).andExpect(status().isForbidden());
        mvc.perform(me.unsafe(put(path), Map.of("name", "Autre")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(me.safe(get(path + "/messages"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-01, ADR 0039 (point 1) : plus de 16 Kio sous /conversations répond 413 REQUEST_REJECTED")
    void sec01BodyAbove16KiBIsRejected() throws Exception {
        ConversationsTestClient me = account("bigm");

        mvc.perform(me.unsafe(
                        post(CONVERSATIONS + "/group"), Map.of("name", "x".repeat(17 * 1024), "memberIds", List.of())))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
    }
}
