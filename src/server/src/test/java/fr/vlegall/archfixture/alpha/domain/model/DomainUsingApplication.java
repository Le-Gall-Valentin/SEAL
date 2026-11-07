package fr.vlegall.archfixture.alpha.domain.model;

import fr.vlegall.archfixture.alpha.application.port.in.AlphaUseCase;

/** Interdit : un domaine qui dépend de l'application. */
public class DomainUsingApplication {
    void call(AlphaUseCase useCase) {
        useCase.run();
    }
}
