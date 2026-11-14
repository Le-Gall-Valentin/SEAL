package fr.vlegall.seal.security.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

/** ADR 0024, point 5 : le serveur renvoie le client pour les seules adresses de ses écrans, lues par GET. */
@SealIntegrationTest
class ClientRoutesIT {

    private static final String INDEX = "/index.html";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @AfterEach
    void resetMail() {
        mail.reset();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/connexion",
                "/inscription",
                "/verification-courriel",
                "/mot-de-passe/oublie",
                "/mot-de-passe/nouveau"
            })
    @DisplayName("AUTH-70, TRN-20 : chaque écran de l'authentification rend le client à un anonyme, avec la CSP")
    void auth70EachScreenAddressServesTheClient(String path) throws Exception {
        mvc.perform(get(path).secure(true))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(INDEX))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("AUTH-16 : une session restreinte ou complète reçoit aussi le client, sans donnée")
    void auth16AnySessionReceivesTheClient() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered account = client.register("ecrans@exemple.ca", "ecrans");
        Cookie restricted = account.session();
        Cookie full = client.verify(account);

        mvc.perform(get("/verification-courriel").secure(true).cookie(restricted))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(INDEX));
        mvc.perform(get("/connexion").secure(true).cookie(full))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(INDEX));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/connexionx",
                "/connexion/",
                "/Connexion",
                "/mot-de-passe",
                "/mot-de-passe/oublie/x",
                "/mot-de-passe/autre"
            })
    @DisplayName("A01 : une adresse voisine garde son comportement, refusée en 401")
    void a01NeighbourAddressesAreStillDenied(String path) throws Exception {
        mvc.perform(get(path).secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(forwardedUrl(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/connexion", "/mot-de-passe/nouveau"})
    @DisplayName("A01 : un POST, même avec son jeton CSRF, n'est pas servi")
    void a01PostIsNotServed(String path) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        mvc.perform(client.unsafe(post(path), null, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(forwardedUrl(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "OPTIONS"})
    @DisplayName("A01 : les écrans ne se lisent que par GET ; HEAD et OPTIONS restent refusés en 401")
    void a01OnlyGetServesTheScreens(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/connexion").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(forwardedUrl(null));
    }
}
