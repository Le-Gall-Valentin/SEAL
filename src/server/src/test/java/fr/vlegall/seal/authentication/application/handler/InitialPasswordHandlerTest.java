package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InitialPasswordHandlerTest {

    private static final UserId USER = new UserId(UUID.fromString("01900000-0000-7000-8000-000000000001"));

    private final InMemoryCredentials credentials = new InMemoryCredentials();
    private final List<String> hashed = new ArrayList<>();
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(Password password) {
            hashed.add(password.value());
            return "hash(" + password.value() + ")";
        }

        @Override
        public boolean matches(Password password, String encoded) {
            return encoded.equals("hash(" + password.value() + ")");
        }

        @Override
        public boolean recognises(String encoded) {
            return encoded.startsWith("hash(");
        }

        @Override
        public void verifyDecoy(Password password) {
            hashed.add("leurre");
        }
    };
    private final InitialPasswordHandler handler = new InitialPasswordHandler(
            new PasswordPreparation(Set.of("correcthorsebatterystaple")::contains, hasher), hasher, credentials);

    @Test
    @DisplayName("AUTH-13 : un mot de passe accepté est haché, normalisé en NFKC, sans rien écrire")
    void auth13AcceptedPasswordIsHashedWithoutWriting() {
        Optional<String> prepared =
                handler.prepare("fjord-tuque-raquette\uFB01", List.of("valentin.le.gall", "valentin"));

        assertThat(prepared).contains("hash(fjord-tuque-raquettefi)");
        assertThat(credentials.hashes()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe refusé n'est pas haché")
    void auth11RejectedPasswordIsNotHashed() {
        assertThat(handler.prepare("valentin.le.gall2026!x", List.of("valentin.le.gall", "valentin")))
                .isEmpty();
        assertThat(handler.prepare("CorrectHorseBatteryStaple", List.of())).isEmpty();
        assertThat(hashed).isEmpty();
    }

    @Test
    @DisplayName("AUTH-13 : une chaîne qui n'est pas un haché préparé n'est jamais enregistrée (échec fermé)")
    void auth13AnythingButAPreparedHashIsRefused() {
        assertThatThrownBy(() -> handler.store(USER, "fjord-tuque-raquette"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(credentials.hashes()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-13 : le haché préparé est enregistré pour le compte")
    void auth13PreparedHashIsStored() {
        handler.store(USER, "hash(fjord-tuque-raquette)");

        assertThat(credentials.hashes()).containsExactly(Map.entry(USER, "hash(fjord-tuque-raquette)"));
    }
}
