package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.keys.domain.model.SubkeyBinding;

/** Table {@code subkeys} : un {@code ekId} ne sert qu'une fois (KEY-05). */
public interface SubkeyRepository {

    /** Enregistre la liaison et sa signature ; faux si l'{@code ekId} existe déjà, sans erreur. */
    boolean save(SubkeyBinding binding, byte[] signature);
}
