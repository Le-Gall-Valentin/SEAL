package fr.vlegall.seal.security.session;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.session.Session;

/**
 * AUTH-52, ADR 0045 : date de création de la première session, reportée à chaque migration par {@link
 * SessionMigration}. Elle seule mesure les 12 heures. Une origine absente ou postérieure à la création de la session ne
 * compte pas : elle ne rajeunit jamais une session. Une origine illisible rend la session expirée (échec fermé).
 */
public final class SessionOrigin {

    /** Attribut de session, en millisecondes Unix. */
    public static final String ATTRIBUTE = SessionOrigin.class.getName() + ".CREATED_AT";

    private SessionOrigin() {}

    /** Origine d'une session de servlet, en millisecondes Unix. */
    public static long createdAt(HttpSession session) {
        return earliest(session.getAttribute(ATTRIBUTE), session.getCreationTime());
    }

    /** Origine d'une session de Spring Session. */
    public static Instant createdAt(Session session) {
        return Instant.ofEpochMilli(earliest(
                session.getAttribute(ATTRIBUTE), session.getCreationTime().toEpochMilli()));
    }

    private static long earliest(@Nullable Object origin, long creation) {
        if (origin == null) {
            return creation;
        }
        // Présente mais illisible : la session est tenue pour expirée (échec fermé).
        return origin instanceof Long millis ? Math.min(millis, creation) : 0L;
    }
}
