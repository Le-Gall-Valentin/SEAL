package fr.vlegall.seal.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lecture des vecteurs de src/protocol/vectors, produits par gen_vectors.py (QA-02). */
public final class ProtocolVectors {

    /** Le répertoire de travail des tests Maven est src/server. */
    private static final Path DIRECTORY = Path.of("..", "protocol", "vectors");

    private final JsonNode document;

    private ProtocolVectors(JsonNode document) {
        this.document = document;
    }

    /** vectors/identity.json (PROTOCOL.md, section 4). */
    public static ProtocolVectors identity() {
        return read("identity.json");
    }

    private static ProtocolVectors read(String file) {
        try {
            return new ProtocolVectors(
                    JsonMapper.builder().build().readTree(Files.readString(DIRECTORY.resolve(file))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Les cas d'une section du fichier ({@code certificates}, {@code proofs}…). Une section absente ou vide est une
     * erreur : sinon, ses tests dynamiques disparaîtraient sans échec, et un encodeur faux resterait vert.
     */
    public Stream<JsonNode> cases(String section) {
        return nonEmpty(section, document.path(section).valueStream().toList());
    }

    /** Les falsifications d'une structure ({@code certificate}, {@code proof}, {@code subkeyBinding}), une au moins. */
    public Stream<JsonNode> forgeries(String structure) {
        return nonEmpty(
                "forgeries de " + structure,
                cases("forgeries")
                        .filter(forgery ->
                                structure.equals(forgery.get("structure").asString()))
                        .toList());
    }

    private static Stream<JsonNode> nonEmpty(String section, List<JsonNode> cases) {
        if (cases.isEmpty()) {
            throw new IllegalStateException("vecteurs : aucun cas dans " + section);
        }
        return cases.stream();
    }

    /** Clé publique brute d'un signataire des vecteurs ({@code ca}, {@code ik}, {@code other}). */
    public byte[] publicKey(String role) {
        return bytes(document.get("keys").get(role), "publicKey");
    }

    /** Section {@code authority} : clé publique, identifiant et empreinte de la CA. */
    public JsonNode authority() {
        return document.get("authority");
    }

    /** Octets d'un champ base64url sans remplissage. */
    public static byte[] bytes(JsonNode node, String field) {
        return Base64.getUrlDecoder().decode(node.get(field).asString());
    }

    public static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asString());
    }

    /**
     * Millisecondes Unix d'une structure signée : un {@code u64} en chaîne décimale (ADR 0006, point 5 ; PROTOCOL.md,
     * 2.5). Un nombre JSON est refusé : au-delà de 2^53, {@code JSON.parse} en perdrait la précision côté client.
     */
    public static Instant instant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + " : u64 attendu en chaîne décimale");
        }
        return Instant.ofEpochMilli(Long.parseUnsignedLong(value.asString()));
    }
}
