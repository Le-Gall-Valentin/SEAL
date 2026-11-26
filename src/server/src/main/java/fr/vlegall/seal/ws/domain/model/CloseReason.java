package fr.vlegall.seal.ws.domain.model;

/** Causes de fermeture d'une connexion par le serveur, et leur code (TRN-12, TRN-13, ADR 0043). */
public enum CloseReason {
    /** TRN-12 : session finie (déconnexion, révocation, expiration), après {@code session.revoked}. */
    SESSION_ENDED(4401),
    /** TRN-13 : aucune trame depuis 60 secondes. */
    SILENT(4408),
    /** TRN-13 : sixième connexion d'un même compte. */
    TOO_MANY_CONNECTIONS(4429);

    private final int code;

    CloseReason(int code) {
        this.code = code;
    }

    /** Code de fermeture WebSocket. */
    public int code() {
        return code;
    }
}
