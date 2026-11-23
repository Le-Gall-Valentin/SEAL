package fr.vlegall.seal.keys.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-21 : le corps de la réinitialisation ne laisse dans une trace ni défi, ni signature, ni coffre. */
@DisplayName("SEC-21 : trace du corps de la réinitialisation")
class ResetIdentityRequestTest {

    private static final String CHALLENGE = "C".repeat(43);
    private static final String SIGNATURE = "S".repeat(86);
    private static final String BLOB = "B".repeat(22);
    private static final String WRAPPED = "W".repeat(64);

    @Test
    @DisplayName("SEC-21, KEY-34 : toString masque le défi, la signature et le coffre, et garde la seule clé publique")
    void sec21ToStringMasksSecrets() {
        String ikPublic = "I".repeat(43);
        ResetIdentityRequest request = new ResetIdentityRequest(
                ikPublic,
                CHALLENGE,
                SIGNATURE,
                new VaultRequest(2L, "A".repeat(43), "N".repeat(16), WRAPPED, "M".repeat(16), BLOB));

        assertThat(request.toString())
                .contains(ikPublic)
                .contains("challenge=***")
                .contains("signature=***")
                .contains("blob=***")
                .doesNotContain(CHALLENGE)
                .doesNotContain(SIGNATURE)
                .doesNotContain(BLOB)
                .doesNotContain(WRAPPED);
    }
}
