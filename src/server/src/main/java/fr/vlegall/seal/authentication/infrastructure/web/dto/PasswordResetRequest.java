package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AUTH-70 : courriel du compte ; borne de SEC-01. Une saisie mal formée ne trouve aucun compte (ADR 0022). */
public record PasswordResetRequest(@NotNull @Size(max = 254) String email) {}
