package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR 0035, point 2 : méthodes de connexion d'un compte. */
@DisplayName("AUTH-55, ADR 0035 : méthodes de connexion")
class LoginMethodsTest {

    @Test
    @DisplayName("AUTH-55, ADR 0035 : les fournisseurs sont rangés dans l'ordre des boutons de la maquette")
    void auth55ProvidersInButtonOrder() {
        LoginMethods methods =
                new LoginMethods(true, List.of(OAuthProvider.GITHUB, OAuthProvider.MICROSOFT, OAuthProvider.GOOGLE));

        assertThat(methods.providers())
                .containsExactly(OAuthProvider.MICROSOFT, OAuthProvider.GOOGLE, OAuthProvider.GITHUB);
    }

    @Test
    @DisplayName("ACC-04 : un fournisseur répété est refusé, un compte n'a qu'une liaison par fournisseur")
    void acc04RepeatedProviderIsRefused() {
        assertThatThrownBy(() -> new LoginMethods(false, List.of(OAuthProvider.GOOGLE, OAuthProvider.GOOGLE)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
