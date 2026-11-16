package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityPublication;

/** KEY-03, KEY-04 : publication de la clé d'identité, prouvée par la signature du défi. */
public interface PublishIdentityUseCase {

    /**
     * Consomme le défi, vérifie la preuve, puis fait certifier {@code ikPublic}. Le défi est consommé même si la preuve
     * est fausse : il ne sert qu'une fois (KEY-03).
     */
    IdentityPublication publish(UserId user, byte[] ikPublic, byte[] challenge, byte[] signature);
}
