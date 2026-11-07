package fr.vlegall.archfixture.alpha.application.handler;

import fr.vlegall.archfixture.beta.application.port.in.BetaUseCase;

/** Interdit : appelle beta depuis la couche application au lieu de passer par un adaptateur. */
public class HandlerCallingBeta {
    void call(BetaUseCase useCase) {
        useCase.run();
    }
}
