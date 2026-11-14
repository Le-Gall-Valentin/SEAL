package fr.vlegall.seal.keys.infrastructure.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** KEY-32, KEY-33, QA-05 : coffre opaque, versionné, du seul compte de la session (ADR 0025, point 6). */
@SealIntegrationTest
class VaultIT {

    private static final String VAULT = "/api/v1/keys/vault";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @AfterEach
    void reset() {
        mail.reset();
    }

    private static Map<String, Object> vault(long version, int blobBytes, int marker) {
        byte[] blob = new byte[blobBytes];
        blob[0] = (byte) marker;
        Map<String, Object> body = new HashMap<>();
        body.put("vaultVersion", version);
        body.put("salt", Base64Url.encode(new byte[32]));
        body.put("nonceK", Base64Url.encode(new byte[12]));
        body.put("wrappedVdk", Base64Url.encode(new byte[48]));
        body.put("nonceB", Base64Url.encode(new byte[12]));
        body.put("blob", Base64Url.encode(blob));
        return body;
    }

    private ResultActions save(KeysTestClient account, Map<String, Object> body) throws Exception {
        return mvc.perform(account.client.unsafe(put(VAULT), account.session, body));
    }

    private ResultActions read(KeysTestClient account) throws Exception {
        return mvc.perform(account.client.from(get(VAULT)).cookie(account.session));
    }

    @Test
    @DisplayName("KEY-33 : sans coffre, 404 KEY_NOT_FOUND ; après le premier PUT, le coffre se relit à l'identique")
    void key33VaultIsAbsentThenReadBack() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "coffre_relu");
        read(account)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));

        save(account, vault(1, 100, 7)).andExpect(status().isNoContent());

        read(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaultVersion").value(1))
                .andExpect(jsonPath("$.blob").value(vault(1, 100, 7).get("blob")))
                .andExpect(jsonPath("$.wrappedVdk").value(vault(1, 100, 7).get("wrappedVdk")));
    }

    @Test
    @DisplayName("KEY-32 : seule la version qui suit l'enregistrée passe ; un rejeu ou un saut donne 409")
    void key32OnlyTheNextVersionIsAccepted() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "coffre_version");
        save(account, vault(1, 64, 1)).andExpect(status().isNoContent());

        save(account, vault(1, 64, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAULT_VERSION_CONFLICT"));
        save(account, vault(3, 64, 3)).andExpect(status().isConflict());
        save(account, vault(2, 64, 4)).andExpect(status().isNoContent());
        save(account, vault(2, 64, 5)).andExpect(status().isConflict());

        read(account)
                .andExpect(jsonPath("$.vaultVersion").value(2))
                .andExpect(jsonPath("$.blob").value(vault(2, 64, 4).get("blob")));
    }

    @Test
    @DisplayName("A01 : chaque compte n'atteint que son propre coffre, aucun identifiant dans l'URL")
    void a01EachAccountReachesOnlyItsVault() throws Exception {
        KeysTestClient owner = KeysTestClient.account(mvc, mail, "coffre_proprio");
        KeysTestClient other = KeysTestClient.account(mvc, mail, "coffre_autre");
        save(owner, vault(1, 64, 9)).andExpect(status().isNoContent());

        read(other).andExpect(status().isNotFound());
        save(other, vault(2, 64, 8)).andExpect(status().isConflict());
        read(owner).andExpect(jsonPath("$.blob").value(vault(1, 64, 9).get("blob")));
    }

    @Test
    @DisplayName(
            "SEC-01, ADR 0025 : blob hors de 16 octets à 32 Kio ou version nulle donnent 400, corps de plus de 64 Kio 413")
    void sec01SizesAreBounded() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "coffre_tailles");
        Map<String, Object> huge = vault(1, 16, 0);
        huge.put("blob", "A".repeat(70_000));

        save(account, vault(1, Vault.MAX_BLOB_BYTES + 1, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("blob")));
        save(account, vault(1, 15, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("blob")));
        save(account, vault(0, 16, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("vaultVersion")));
        save(account, huge)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
        save(account, vault(1, Vault.MAX_BLOB_BYTES, 0)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("QA-05 : sans session 401, sans jeton CSRF 403, en session restreinte 403")
    void qa05AccessIsChecked() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted = client.register("coffre_restreint@exemple.ca", "coffre_restreint")
                .session();
        KeysTestClient account = KeysTestClient.account(mvc, mail, "coffre_csrf");

        mvc.perform(client.from(get(VAULT))).andExpect(status().isUnauthorized());
        mvc.perform(client.unsafe(put(VAULT), null, vault(1, 16, 0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.from(get(VAULT)).cookie(restricted))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(put(VAULT)).cookie(account.session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
