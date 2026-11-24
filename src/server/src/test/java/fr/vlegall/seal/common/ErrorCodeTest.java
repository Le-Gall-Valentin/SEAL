package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
        "400, VALIDATION_FAILED",
        "401, UNAUTHENTICATED",
        "403, FORBIDDEN",
        "404, NOT_FOUND",
        "429, RATE_LIMITED",
        "500, INTERNAL_ERROR",
        "503, INTERNAL_ERROR",
        "405, REQUEST_REJECTED",
        "415, REQUEST_REJECTED"
    })
    @DisplayName("ADR 0010 : chaque statut d'erreur reçoit son code ; tout 5xx est une erreur interne")
    void forStatusMapsEachStatus(int status, ErrorCode expected) {
        assertThat(ErrorCode.forStatus(status)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"KEY_NOT_FOUND, 404", "INVALID_SIGNATURE, 422", "VAULT_VERSION_CONFLICT, 409"})
    @DisplayName("ADR 0025, point 5 : chaque code de la cryptographie a son statut")
    void adr0025CryptographyCodesHaveTheirStatus(ErrorCode code, int status) {
        assertThat(code.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @CsvSource({"NOT_FRIENDS, 403", "NOT_A_MEMBER, 404", "GROUP_FULL, 409"})
    @DisplayName("CNV-01, GRP-02, GRP-03, ADR 0039 (points 2 et 3) : chaque code des conversations a son statut")
    void adr0039ConversationCodesHaveTheirStatus(ErrorCode code, int status) {
        assertThat(code.status()).isEqualTo(status);
    }
}
