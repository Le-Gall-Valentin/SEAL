package fr.vlegall.seal.keys.domain.model;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Canonical;
import fr.vlegall.seal.crypto.Ed25519;
import java.util.Arrays;
import java.util.Objects;

/**
 * Preuve de possession de la clé d'identité (KEY-03, PROTOCOL.md section 4.3), équivalent du CSR : le compte signe
 * avec IK le défi que le serveur lui a donné. {@code user} vient de la session, jamais du corps de la requête.
 */
public record ProofOfPossession(UserId user, byte[] ikPublic, byte[] challenge) {

    /** Étiquette de la structure : elle fixe aussi l'algorithme, Ed25519 (ADR 0025, point 11). */
    public static final String LABEL = "SEAL-pop-v1";

    /** KEY-03 : défi de 32 octets. */
    public static final int CHALLENGE_BYTES = 32;

    public ProofOfPossession {
        Objects.requireNonNull(user, "user");
        if (Objects.requireNonNull(ikPublic, "ikPublic").length != Ed25519.PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("ikPublic fait 32 octets");
        }
        if (Objects.requireNonNull(challenge, "challenge").length != CHALLENGE_BYTES) {
            throw new IllegalArgumentException("le défi fait 32 octets");
        }
        ikPublic = ikPublic.clone();
        challenge = challenge.clone();
    }

    /** Octets signés par IK. */
    public byte[] encode() {
        return Canonical.struct(
                LABEL, Canonical.uuid(user.value()), Canonical.bytes(ikPublic), Canonical.bytes(challenge));
    }

    /** Vrai si {@code signature} est celle de la preuve par la clé qu'elle présente. */
    public boolean isSignedBy(byte[] signature) {
        return Ed25519.verify(ikPublic, encode(), signature);
    }

    @Override
    public byte[] ikPublic() {
        return ikPublic.clone();
    }

    @Override
    public byte[] challenge() {
        return challenge.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProofOfPossession that && Arrays.equals(encode(), that.encode());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encode());
    }

    /** Le défi n'apparaît dans aucune trace (SEC-21). */
    @Override
    public String toString() {
        return "ProofOfPossession[user=" + user.value() + "]";
    }
}
