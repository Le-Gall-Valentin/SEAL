package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.UserId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InitialPasswordHandlerTest {

    private static final UserId USER = new UserId(UUID.fromString("01900000-0000-7000-8000-000000000001"));

    private final Map<UserId, String> stored = new HashMap<>();
    private final CredentialRepository credentials = stored::put;
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(Password password) {
            return "hash(" + password.value() + ")";
        }

        @Override
        public boolean matches(Password password, String encoded) {
            return encoded.equals(hash(password));
        }
    };
    private final InitialPasswordHandler handler =
            new InitialPasswordHandler(Set.of("correcthorsebatterystaple")::contains, hasher, credentials);

    @Test
    @DisplayName("AUTH-13 : un mot de passe accepté est enregistré haché, normalisé en NFKC")
    void auth13AcceptedPasswordIsStoredHashed() {
        boolean accepted = handler.establish(USER, "fjord-tuque-raquetteﬁ", List.of("valentin.le.gall", "valentin"));

        assertThat(accepted).isTrue();
        assertThat(stored).containsEntry(USER, "hash(fjord-tuque-raquettefi)");
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe refusé n'est ni haché ni enregistré")
    void auth11RejectedPasswordIsNotStored() {
        assertThat(handler.establish(USER, "valentin.le.gall2026!x", List.of("valentin.le.gall", "valentin")))
                .isFalse();
        assertThat(handler.establish(USER, "CorrectHorseBatteryStaple", List.of()))
                .isFalse();
        assertThat(stored).isEmpty();
    }
}
