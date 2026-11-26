package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.TestAccount;
import fr.vlegall.seal.support.TestWebSocketClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;

/** TRN-10, TRN-11 : la poignée de main de /ws, et le scénario « Détournement de WebSocket » de 16.1. */
@SealServerTest
class WebSocketHandshakeIT {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    private TestWebSocketClient ws() {
        return new TestWebSocketClient(port);
    }

    @Test
    @DisplayName("TRN-10, TRN-11 : une session complète de la même origine ouvre /ws")
    void trn11FullSessionFromTheSameOriginOpens() throws Exception {
        TestAccount account = TestAccount.fresh(mvc, mail, "ws");

        assertThat(ws().handshakeStatus(account.session, TestWebSocketClient.ORIGIN))
                .isEqualTo(101);
    }

    @Test
    @DisplayName("TRN-11 : un anonyme est refusé en 403")
    void trn11AnonymousIsRefused() throws Exception {
        assertThat(ws().handshakeStatus(null, TestWebSocketClient.ORIGIN)).isEqualTo(403);
    }

    @Test
    @DisplayName("TRN-11, ADR 0007 : une session restreinte (courriel non vérifié) est refusée en 403")
    void trn11RestrictedSessionIsRefused() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        String username = TestAccount.name("ws");
        AuthClient.Registered pending = client.register(username + "@exemple.ca", username);

        assertThat(ws().handshakeStatus(pending.session(), TestWebSocketClient.ORIGIN))
                .isEqualTo(403);
    }

    @Test
    @DisplayName("TRN-11, 16.1 : la poignée de main depuis une autre origine est refusée en 403")
    void trn11OtherOriginIsRefused() throws Exception {
        TestAccount account = TestAccount.fresh(mvc, mail, "ws");

        assertThat(ws().handshakeStatus(account.session, "https://evil.example"))
                .isEqualTo(403);
        assertThat(ws().handshakeStatus(account.session, "http://localhost:8443"))
                .isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "https://localhost:8443.evil.example",
                "HTTPS://LOCALHOST:8443",
                "https://LOCALHOST:8443",
                "https://localhost:8443/",
                "https://localhost",
                "https://127.0.0.1:8443",
                ""
            })
    @DisplayName(
            "TRN-11, 16.1 : une origine nulle, suffixée, en majuscules, avec chemin ou autre hôte est refusée en 403")
    void trn11OriginVariantsAreRefused(String origin) throws Exception {
        TestAccount account = TestAccount.fresh(mvc, mail, "ws");

        assertThat(ws().handshakeStatus(account.session, origin)).isEqualTo(403);
    }

    @Test
    @DisplayName("TRN-11 : une poignée de main sans en-tête Origin est refusée en 403")
    void trn11MissingOriginIsRefused() throws Exception {
        TestAccount account = TestAccount.fresh(mvc, mail, "ws");

        assertThat(ws().handshakeStatus(account.session, null)).isEqualTo(403);
    }
}
