package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** SEC-20 : publications de clés, écrites dans la transaction de la publication (ADR 0025, point 10). */
public interface KeyEventPort {

    void identityPublished(UserId user, byte[] serial);

    void subkeyPublished(UserId user, byte[] ekId);
}
