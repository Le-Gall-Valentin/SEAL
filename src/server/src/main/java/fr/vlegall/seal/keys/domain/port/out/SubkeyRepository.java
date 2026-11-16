package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import java.time.Instant;

/** Table {@code subkeys} : un {@code ekId} ne sert qu'une fois (KEY-05), trois sous-clés vivantes au plus par certificat (ADR 0025). */
public interface SubkeyRepository {

    /**
     * ADR 0025, point 15 : verrou des sous-clés du compte, tenu jusqu'à la fin de la transaction en cours ; deux
     * publications du même compte comptent et écrivent l'une après l'autre.
     */
    void lockAccount(UserId user);

    /**
     * Sous-clés du compte liées au certificat {@code certSerial} et non expirées à {@code at} ({@code expiresAt} exclu) :
     * celles d'un ancien certificat ne comptent pas (ADR 0025, point 15).
     */
    int countLive(UserId user, byte[] certSerial, Instant at);

    /** Enregistre la liaison et sa signature ; faux si l'{@code ekId} existe déjà, sans erreur. */
    boolean save(SubkeyBinding binding, byte[] signature);
}
