package fr.vlegall.archfixture.alpha.domain.model;

import java.io.Serial;
import java.io.Serializable;

/** Contre-exemple : un modèle du domaine sérialisable, qui lierait le domaine au format d'une session. */
public record SerializableThing(String name) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
