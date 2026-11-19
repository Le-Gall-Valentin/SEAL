package fr.vlegall.seal.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;
import java.util.UUID;

/**
 * Relecture d'une structure écrite par {@link Canonical} (PROTOCOL.md, section 2), champ par champ et dans l'ordre :
 * le serveur relit ainsi un certificat qu'il a lui-même encodé (KEY-33). Toute valeur hors du format lève
 * {@link CanonicalEncodingException} : longueur au-delà des octets restants, u64 au-delà de 2^63 - 1, UTF-8 invalide
 * ou hors de la forme NFC, autre étiquette, octet en trop après {@link #end()}.
 */
public final class CanonicalReader {

    private static final int UUID_BYTES = 16;

    private final ByteBuffer buffer;

    private CanonicalReader(byte[] encoded) {
        this.buffer =
                ByteBuffer.wrap(Objects.requireNonNull(encoded, "octets nuls").clone());
    }

    /** Ouvre la structure {@code struct(label, …)} et vérifie son étiquette. */
    public static CanonicalReader of(byte[] encoded, String label) {
        CanonicalReader reader = new CanonicalReader(encoded);
        if (!reader.text().equals(label)) {
            throw new CanonicalEncodingException("struct : étiquette inattendue");
        }
        return reader;
    }

    public int u8() {
        return Byte.toUnsignedInt(take(Byte.BYTES)[0]);
    }

    public long u64() {
        long value = ByteBuffer.wrap(take(Long.BYTES)).getLong();
        if (value < 0) {
            throw new CanonicalEncodingException("u64 au-delà de 2^63 - 1");
        }
        return value;
    }

    public byte[] bytes() {
        long length =
                Integer.toUnsignedLong(ByteBuffer.wrap(take(Integer.BYTES)).getInt());
        if (length > buffer.remaining()) {
            throw new CanonicalEncodingException("bytes : longueur au-delà des octets restants");
        }
        return take((int) length);
    }

    /** PROTOCOL.md 2.1, ADR 0006 : UTF-8 strict, en forme NFC, comme l'écrit {@link Canonical#text(String)}. */
    public String text() {
        String value;
        try {
            value = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CanonicalEncodingException("text : UTF-8 invalide");
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new CanonicalEncodingException("text : hors de la forme NFC");
        }
        return value;
    }

    public UUID uuid() {
        ByteBuffer value = ByteBuffer.wrap(take(UUID_BYTES));
        return new UUID(value.getLong(), value.getLong());
    }

    /** Exige qu'aucun octet ne suive le dernier champ. */
    public void end() {
        if (buffer.hasRemaining()) {
            throw new CanonicalEncodingException("octets en trop après la structure");
        }
    }

    private byte[] take(int length) {
        if (length > buffer.remaining()) {
            throw new CanonicalEncodingException("structure tronquée");
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return value;
    }
}
