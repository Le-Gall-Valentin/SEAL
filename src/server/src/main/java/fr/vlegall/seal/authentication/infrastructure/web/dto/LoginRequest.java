package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AUTH-20 : identifiant ou courriel, et mot de passe ; bornes de SEC-01 (ADR 0021, point 3). */
public record LoginRequest(
        @NotNull @Size(max = 254) String login,
        @NotNull @Size(max = 512) String password) {

    @Override
    public String toString() {
        return "LoginRequest[password=***]";
    }
}
