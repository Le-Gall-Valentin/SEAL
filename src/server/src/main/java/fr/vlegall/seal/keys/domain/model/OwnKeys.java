package fr.vlegall.seal.keys.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * KEY-33 : ce qu'un nouvel appareil relit de ses propres clés publiées : le certificat courant, qu'il compare à son IK
 * restaurée, et les sous-clés non expirées liées à ce certificat, les plus récentes d'abord (ADR 0029, point 4).
 */
public record OwnKeys(IdentityCertificate certificate, List<PublishedSubkey> subkeys) {

    public OwnKeys {
        Objects.requireNonNull(certificate, "certificate");
        subkeys = List.copyOf(subkeys);
    }
}
