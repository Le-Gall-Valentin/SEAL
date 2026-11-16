package fr.vlegall.seal.pki.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** KEY-10, KEY-11, QA-05 : {@code GET /api/v1/pki/ca}, pour la seule session complète (ADR 0025, point 9). */
@SealIntegrationTest
class PkiIT {

    private static final String CA = "/api/v1/pki/ca";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private AuthoritySigner authority;

    @AfterEach
    void resetMail() {
        mail.reset();
    }

    @Test
    @DisplayName("KEY-11 : la session complète lit la clé publique, l'identifiant et l'empreinte de l'Autorité")
    void key11FullSessionReadsTheAuthority() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie full = client.verify(client.register("pki.ca@exemple.ca", "pki_ca"));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

        mvc.perform(get(CA).secure(true).cookie(full))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey")
                        .value(encoder.encodeToString(authority.key().publicKey())))
                .andExpect(jsonPath("$.keyId")
                        .value(encoder.encodeToString(authority.key().keyId())))
                .andExpect(jsonPath("$.fingerprint").value(authority.key().fingerprint()))
                .andExpect(jsonPath("$.fingerprint").value(org.hamcrest.Matchers.matchesPattern("[0-9A-F]{64}")));
    }

    @Test
    @DisplayName("QA-05 : sans session, 401 UNAUTHENTICATED")
    void qa05AnonymousIsUnauthenticated() throws Exception {
        mvc.perform(get(CA).secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AUTH-16, QA-05 : une session restreinte reçoit 403 FORBIDDEN")
    void qa05RestrictedSessionIsForbidden() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted =
                client.register("pki.restreint@exemple.ca", "pki_restreint").session();

        mvc.perform(get(CA).secure(true).cookie(restricted))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
