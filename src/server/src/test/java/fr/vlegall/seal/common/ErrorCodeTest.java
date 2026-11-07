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
}
