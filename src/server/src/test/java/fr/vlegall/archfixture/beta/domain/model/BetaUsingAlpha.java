package fr.vlegall.archfixture.beta.domain.model;

import fr.vlegall.archfixture.alpha.domain.model.AlphaThing;

/** Interdit : le domaine de beta dépend de celui d'alpha, qui dépend de beta ; un vrai cycle (règle 9). */
public class BetaUsingAlpha {
    AlphaThing other;
}
