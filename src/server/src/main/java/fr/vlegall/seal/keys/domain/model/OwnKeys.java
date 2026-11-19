package fr.vlegall.seal.keys.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * KEY-33 : ce qu'un nouvel appareil relit de ses propres clés publiées : le certificat courant, qu'il compare à son IK
 * restaurée, et les sous-clés non expirées liées à ce certificat, les plus récentes d'abord (ADR 0029, point 4).
 */
public record OwnKeys(IdentityCertificate certificate, List<PublishedSubkey> subkeys) {

    /**
     * Chaque sous-clé est liée à ce certificat et à son titulaire : c'est ce qui permet de les servir sans leur
     * {@code certSerial}, que le client lit dans le certificat (PROTOCOL.md 4.4).
     */
    public OwnKeys {
        Objects.requireNonNull(certificate, "certificate");
        subkeys = List.copyOf(subkeys);
        for (PublishedSubkey subkey : subkeys) {
            SubkeyBinding binding = subkey.binding();
            if (!certificate.hasSerial(binding.certSerial()) || !binding.user().equals(certificate.subject())) {
                throw new IllegalArgumentException("sous-clé liée à un autre certificat");
            }
        }
    }
}
