package fr.vlegall.seal.keys.domain.model;

import static fr.vlegall.seal.support.ProtocolVectors.bytes;
import static fr.vlegall.seal.support.ProtocolVectors.instant;
import static fr.vlegall.seal.support.ProtocolVectors.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.ProtocolVectors;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/** KEY-03, KEY-05, QA-02 : preuve de possession et liaison de sous-clé (PROTOCOL.md, sections 4.3 et 4.4). */
@DisplayName("KEY-03, KEY-05 : preuve de possession et liaison de sous-clé")
class SignedKeyStructuresTest {

    private static final ProtocolVectors VECTORS = ProtocolVectors.identity();
    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    static ProofOfPossession proof(JsonNode fields) {
        return new ProofOfPossession(
                new UserId(uuid(fields, "userId")), bytes(fields, "ikPublic"), bytes(fields, "challenge"));
    }

    static SubkeyBinding binding(JsonNode fields) {
        return new SubkeyBinding(
                new UserId(uuid(fields, "userId")),
                bytes(fields, "certSerial"),
                bytes(fields, "ekId"),
                fields.get("kem").asString(),
                bytes(fields, "ekPublic"),
                instant(fields, "createdAt"),
                instant(fields, "expiresAt"));
    }

    @TestFactory
    @DisplayName("QA-02 : la preuve des vecteurs est encodée à l'octet près, et sa signature la vérifie")
    Stream<DynamicTest> qa02ProofsMatchTheVectors() {
        return VECTORS.cases("proofs")
                .map(vector -> DynamicTest.dynamicTest(vector.get("name").asString(), () -> {
                    ProofOfPossession proof = proof(vector.get("fields"));
                    assertThat(proof.encode()).isEqualTo(bytes(vector, "encoded"));
                    assertThat(proof.isSignedBy(bytes(vector, "signature"))).isTrue();
                }));
    }

    @TestFactory
    @DisplayName("QA-02 : la liaison des vecteurs est encodée à l'octet près, et la signature de l'IK la vérifie")
    Stream<DynamicTest> qa02BindingsMatchTheVectors() {
        byte[] ik = VECTORS.publicKey("ik");
        return VECTORS.cases("subkeyBindings")
                .map(vector -> DynamicTest.dynamicTest(vector.get("name").asString(), () -> {
                    SubkeyBinding binding = binding(vector.get("fields"));
                    assertThat(binding.encode()).isEqualTo(bytes(vector, "encoded"));
                    assertThat(binding.isSignedBy(ik, bytes(vector, "signature")))
                            .isTrue();
                }));
    }

    @TestFactory
    @DisplayName("QA-04 : une preuve falsifiée est refusée")
    Stream<DynamicTest> qa04ProofForgeriesAreRejected() {
        return VECTORS.forgeries("proof")
                .map(forgery -> DynamicTest.dynamicTest(
                        forgery.get("name").asString(),
                        () -> assertThat(proof(forgery.get("fields")).isSignedBy(bytes(forgery, "signature")))
                                .isFalse()));
    }

    @TestFactory
    @DisplayName("QA-04 : une liaison falsifiée est refusée")
    Stream<DynamicTest> qa04BindingForgeriesAreRejected() {
        return VECTORS.forgeries("subkeyBinding")
                .map(forgery -> DynamicTest.dynamicTest(forgery.get("name").asString(), () -> {
                    byte[] verifier = VECTORS.publicKey(forgery.get("verifier").asString());
                    assertThat(binding(forgery.get("fields")).isSignedBy(verifier, bytes(forgery, "signature")))
                            .isFalse();
                }));
    }

    @Test
    @DisplayName("KEY-03 : une preuve hors du format est refusée à la construction")
    void key03MalformedProofIsRefused() {
        assertThatThrownBy(() -> new ProofOfPossession(USER, new byte[31], new byte[32]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProofOfPossession(USER, new byte[32], new byte[33]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KEY-05 : une liaison hors du format est refusée à la construction")
    void key05MalformedBindingIsRefused() {
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);
        String kem = SubkeyBinding.KEM;

        assertThatThrownBy(() -> new SubkeyBinding(USER, new byte[15], new byte[16], kem, new byte[32], NOW, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SubkeyBinding(USER, new byte[16], new byte[17], kem, new byte[32], NOW, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SubkeyBinding(USER, new byte[16], new byte[16], kem, new byte[33], NOW, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SubkeyBinding(USER, new byte[16], new byte[16], "", new byte[32], NOW, expires))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KEY-03 : la preuve garde ses octets, même si l'appelant modifie les siens")
    void key03ProofIsImmutable() {
        byte[] ikPublic = new byte[32];
        ProofOfPossession proof = new ProofOfPossession(USER, ikPublic, new byte[32]);
        byte[] before = proof.encode();
        ikPublic[0] = 1;
        proof.ikPublic()[1] = 1;
        proof.challenge()[0] = 1;

        assertThat(proof.encode()).isEqualTo(before);
        assertThat(proof).isEqualTo(new ProofOfPossession(USER, new byte[32], new byte[32]));
        assertThat(proof).hasSameHashCodeAs(new ProofOfPossession(USER, new byte[32], new byte[32]));
    }

    @Test
    @DisplayName("KEY-05 : la liaison garde ses octets, même si l'appelant modifie les siens")
    void key05BindingIsImmutable() {
        byte[] ekPublic = new byte[32];
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);
        SubkeyBinding binding =
                new SubkeyBinding(USER, new byte[16], new byte[16], SubkeyBinding.KEM, ekPublic, NOW, expires);
        byte[] before = binding.encode();
        ekPublic[0] = 1;
        binding.ekPublic()[1] = 1;
        binding.ekId()[0] = 1;
        binding.certSerial()[0] = 1;

        assertThat(binding.encode()).isEqualTo(before);
        SubkeyBinding same =
                new SubkeyBinding(USER, new byte[16], new byte[16], SubkeyBinding.KEM, new byte[32], NOW, expires);
        assertThat(binding).isEqualTo(same).hasSameHashCodeAs(same);
    }

    @Test
    @DisplayName("SEC-21 : la preuve n'affiche pas son défi ; preuve et liaison ne valent que pour leur propre type")
    void sec21StructuresShowNoSecret() {
        byte[] challenge = new byte[32];
        challenge[0] = 0x7f;
        ProofOfPossession proof = new ProofOfPossession(USER, new byte[32], challenge);
        SubkeyBinding binding = new SubkeyBinding(
                USER,
                new byte[16],
                new byte[16],
                SubkeyBinding.KEM,
                new byte[32],
                NOW,
                NOW.plus(SubkeyBinding.VALIDITY));

        assertThat(proof.toString()).contains(USER.value().toString()).doesNotContain("7f");
        assertThat(binding.toString()).contains(USER.value().toString());
        assertThat(proof).isNotEqualTo(binding);
        assertThat(binding).isNotEqualTo(proof);
    }
}
