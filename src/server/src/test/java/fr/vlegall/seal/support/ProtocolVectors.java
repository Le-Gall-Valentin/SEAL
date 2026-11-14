package fr.vlegall.seal.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
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

    /** Les cas d'une section du fichier ({@code certificates}, {@code proofs}…). */
    public Stream<JsonNode> cases(String section) {
        return document.get(section).valueStream();
    }

    /** Les falsifications d'une structure ({@code certificate}, {@code proof}, {@code subkeyBinding}). */
    public Stream<JsonNode> forgeries(String structure) {
        return cases("forgeries")
                .filter(forgery -> structure.equals(forgery.get("structure").asString()));
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

    /** Millisecondes Unix d'une structure signée. */
    public static Instant instant(JsonNode node, String field) {
        return Instant.ofEpochMilli(node.get(field).longValue());
    }
}
