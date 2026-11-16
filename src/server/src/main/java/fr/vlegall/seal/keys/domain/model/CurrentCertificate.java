package fr.vlegall.seal.keys.domain.model;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Certificat valide d'un compte, vu de {@code keys} : son numéro de série et la clé d'identité qu'il atteste. */
public record CurrentCertificate(byte[] serial, byte[] ikPublic) {

    public CurrentCertificate {
        serial = Objects.requireNonNull(serial, "serial").clone();
        ikPublic = Objects.requireNonNull(ikPublic, "ikPublic").clone();
    }

    /** Vrai si {@code candidate} est le numéro de série de ce certificat. */
    public boolean hasSerial(byte[] candidate) {
        return Arrays.equals(serial, candidate);
    }

    @Override
    public byte[] serial() {
        return serial.clone();
    }

    @Override
    public byte[] ikPublic() {
        return ikPublic.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CurrentCertificate that
                && Arrays.equals(serial, that.serial)
                && Arrays.equals(ikPublic, that.ikPublic);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serial);
    }

    @Override
    public String toString() {
        return "CurrentCertificate[serial=" + HexFormat.of().formatHex(serial) + "]";
    }
}
