package fr.vlegall.archfixture.alpha.infrastructure.beta;

import fr.vlegall.archfixture.beta.domain.model.BetaThing;

/** Interdit : atteint le modèle de beta au lieu de son cas d'usage. */
public class AdapterReachingBetaDomain {
    Object reach() {
        return new BetaThing();
    }
}
