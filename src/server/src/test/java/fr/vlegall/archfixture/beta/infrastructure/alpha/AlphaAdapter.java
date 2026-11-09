package fr.vlegall.archfixture.beta.infrastructure.alpha;

import fr.vlegall.archfixture.alpha.application.port.in.AlphaUseCase;

/**
 * Permis : l'infrastructure de beta appelle un cas d'usage d'alpha, alors qu'alpha appelle aussi beta
 * ({@code BetaAdapter}). Les deux contextes s'appellent par leurs adaptateurs, sans cycle interdit (règle 9).
 */
public class AlphaAdapter {
    AlphaUseCase useCase;
}
