package fr.vlegall.archfixture.alpha.infrastructure.web;

import fr.vlegall.archfixture.beta.application.port.in.BetaUseCase;

/** Interdit : un autre contexte appelé depuis la couche web au lieu de infrastructure/beta. */
public class ControllerCallingBeta {
    void call(BetaUseCase useCase) {
        useCase.run();
    }
}
