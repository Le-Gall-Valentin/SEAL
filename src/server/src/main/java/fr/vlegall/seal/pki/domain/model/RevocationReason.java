package fr.vlegall.seal.pki.domain.model;

/** Motif de révocation, avec le code de X.509 (RFC 5280, CRLReason) rangé dans {@code revocation_reason}. */
public enum RevocationReason {
    /** ADR 0025, point 4 : certificat expiré, remplacé par un nouveau. */
    SUPERSEDED(4);

    private final int code;

    RevocationReason(int code) {
        this.code = code;
    }

    /** Code de X.509. */
    public int code() {
        return code;
    }
}
