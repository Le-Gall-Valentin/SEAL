package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.ProtocolVectors;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.HexFormat;
import javax.crypto.KeyAgreement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * QA-01 : X25519 de la JCA (XDH) sur les vecteurs officiels de la RFC 7748 (section 6.1). Le client s'en sert pour ses
 * sous-clés (KEY-05) ; le serveur n'en vérifie que la longueur (ADR 0025, point 7).
 */
@DisplayName("QA-01 : X25519")
class X25519Rfc7748Test {

    private static final HexFormat HEX = HexFormat.of();
    private static final ProtocolVectors RFC7748 = ProtocolVectors.x25519Rfc7748();

    private static byte[] hex(JsonNode node, String field) {
        return HEX.parseHex(node.get(field).asString());
    }

    private static PrivateKey privateKey(byte[] scalar) throws Exception {
        return KeyFactory.getInstance("XDH").generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, scalar));
    }

    /** RFC 7748, section 5 : u en petit-boutiste, bit de poids fort ignoré. */
    private static PublicKey publicKey(byte[] u) throws Exception {
        byte[] bigEndian = new byte[u.length];
        for (int i = 0; i < u.length; i++) {
            bigEndian[i] = u[u.length - 1 - i];
        }
        bigEndian[0] &= 0x7f;
        return KeyFactory.getInstance("XDH")
                .generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, bigEndian)));
    }

    private static byte[] agree(PrivateKey own, PublicKey peer) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("XDH");
        agreement.init(own);
        agreement.doPhase(peer, true);
        return agreement.generateSecret();
    }

    @Test
    @DisplayName("QA-01 : RFC 7748, 6.1 : chaque clé publique vaut X25519(clé privée, 9)")
    void qa01PublicKeysAreTheBasePointMultiples() throws Exception {
        byte[] basePoint = new byte[32];
        basePoint[0] = 9;
        for (String party : new String[] {"alice", "bob"}) {
            JsonNode keys = RFC7748.section(party);

            assertThat(agree(privateKey(hex(keys, "privateKey")), publicKey(basePoint)))
                    .as(party)
                    .isEqualTo(hex(keys, "publicKey"));
        }
    }

    @Test
    @DisplayName("QA-01 : RFC 7748, 6.1 : Alice et Bob dérivent le même secret K")
    void qa01AliceAndBobDeriveTheSameSecret() throws Exception {
        JsonNode alice = RFC7748.section("alice");
        JsonNode bob = RFC7748.section("bob");
        byte[] shared = HEX.parseHex(RFC7748.value("sharedSecret").asString());

        assertThat(agree(privateKey(hex(alice, "privateKey")), publicKey(hex(bob, "publicKey"))))
                .isEqualTo(shared);
        assertThat(agree(privateKey(hex(bob, "privateKey")), publicKey(hex(alice, "publicKey"))))
                .isEqualTo(shared);
    }
}
