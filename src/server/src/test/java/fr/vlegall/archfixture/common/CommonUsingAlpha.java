package fr.vlegall.archfixture.common;

import fr.vlegall.archfixture.alpha.domain.model.AlphaThing;

/** Interdit : un package technique qui dépend d'un contexte. */
public class CommonUsingAlpha {
    String name(AlphaThing thing) {
        return thing.name();
    }
}
