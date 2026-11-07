package fr.vlegall.archfixture.alpha.infrastructure.web;

import fr.vlegall.archfixture.alpha.application.port.in.AlphaUseCase;

/** Permis : un contrôleur qui appelle un cas d'usage. */
public class ControllerUsingUseCase {
    void call(AlphaUseCase useCase) {
        useCase.run();
    }
}
