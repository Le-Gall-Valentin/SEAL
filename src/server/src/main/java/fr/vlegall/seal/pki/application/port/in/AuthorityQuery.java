package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.pki.domain.model.AuthorityKey;

/** KEY-10, KEY-11 : clé publique de l'Autorité, que le client épingle (lue par le contrôleur de {@code pki}). */
public interface AuthorityQuery {

    AuthorityKey authority();
}
