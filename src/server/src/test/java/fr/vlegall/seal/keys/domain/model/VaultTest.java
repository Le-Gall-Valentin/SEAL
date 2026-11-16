package fr.vlegall.seal.keys.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-32, ADR 0025 (point 6) : tailles du coffre, opaque pour le serveur. */
@DisplayName("KEY-32 : coffre de clés")
class VaultTest {

    private static Vault vault(long version, int blob) {
        return new Vault(version, new byte[32], new byte[12], new byte[48], new byte[12], new byte[blob]);
    }

    @Test
    @DisplayName("KEY-32 : un coffre de 16 octets à 32 Kio, en version 1 ou plus, est accepté")
    void key32ValidVaultIsAccepted() {
        assertThat(vault(1, Vault.MIN_BLOB_BYTES).blob()).hasSize(16);
        assertThat(vault(2, Vault.MAX_BLOB_BYTES).blob()).hasSize(32 * 1024);
    }

    @Test
    @DisplayName("ADR 0025, point 6 : version nulle, tailles fausses ou blob démesuré refusés")
    void adr0025MalformedVaultIsRefused() {
        assertThatThrownBy(() -> vault(0, 16)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vault(1, 15)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vault(1, Vault.MAX_BLOB_BYTES + 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Vault(1, new byte[31], new byte[12], new byte[48], new byte[12], new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Vault(1, new byte[32], new byte[11], new byte[48], new byte[12], new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Vault(1, new byte[32], new byte[12], new byte[47], new byte[12], new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Vault(1, new byte[32], new byte[12], new byte[48], new byte[13], new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SEC-21 : le coffre garde ses octets, se compare par eux, et ne montre que sa version et sa taille")
    void sec21VaultIsAValueThatShowsNothing() {
        byte[] blob = new byte[16];
        Vault vault = new Vault(3, new byte[32], new byte[12], new byte[48], new byte[12], blob);
        blob[0] = 1;
        vault.blob()[1] = 1;

        assertThat(vault)
                .isEqualTo(vault(3, 16))
                .hasSameHashCodeAs(vault(3, 16))
                .isNotEqualTo(vault(4, 16));
        assertThat(vault.toString()).isEqualTo("Vault[version=3, blob=16 octets]");
    }
}
