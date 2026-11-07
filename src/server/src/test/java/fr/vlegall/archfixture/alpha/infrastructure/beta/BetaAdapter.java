package fr.vlegall.archfixture.alpha.infrastructure.beta;

import fr.vlegall.archfixture.beta.application.port.in.BetaUseCase;

/** Permis : l'infrastructure d'alpha appelle un cas d'usage de beta. */
public class BetaAdapter {
    void call(BetaUseCase useCase) {
        useCase.run();
    }
}
