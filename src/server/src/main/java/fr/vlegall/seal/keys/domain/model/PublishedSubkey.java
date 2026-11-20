package fr.vlegall.seal.keys.domain.model;

import java.util.Arrays;
import java.util.Objects;

/** Sous-clé publiée (KEY-05) : sa liaison et la signature de l'IK, telles que le serveur les a vérifiées et gardées. */
public record PublishedSubkey(SubkeyBinding binding, byte[] signature) {

    public PublishedSubkey {
        Objects.requireNonNull(binding, "binding");
        signature = Objects.requireNonNull(signature, "signature").clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PublishedSubkey that
                && binding.equals(that.binding)
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return 31 * binding.hashCode() + Arrays.hashCode(signature);
    }
}
