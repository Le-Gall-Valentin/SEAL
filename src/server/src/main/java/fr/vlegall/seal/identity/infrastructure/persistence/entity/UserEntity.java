package fr.vlegall.seal.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ligne de {@code users}, en lecture : l'insertion passe par SQL, pour que PostgreSQL attribue l'UUIDv7 (DATA-03), et les
 * mises à jour aussi, pour qu'un refus de l'index partiel survienne dans le handler et non au commit (ADR 0019).
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    /** AUTH-03 : colonne non modifiable côté JPA, doublée par le déclencheur PostgreSQL. */
    @Column(name = "username", nullable = false, insertable = false, updatable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "email", columnDefinition = "citext")
    private @Nullable String email;

    @Column(name = "email_verified_at")
    private @Nullable Instant emailVerifiedAt;

    public @Nullable Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    @Column(name = "status", nullable = false)
    private String status;

    protected UserEntity() {
        // JPA
        this.id = new UUID(0, 0);
        this.username = "";
        this.displayName = "";
        this.status = "";
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public @Nullable String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }
}
