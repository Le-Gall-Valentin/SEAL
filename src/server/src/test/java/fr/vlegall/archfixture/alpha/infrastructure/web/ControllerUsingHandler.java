package fr.vlegall.archfixture.alpha.infrastructure.web;

import fr.vlegall.archfixture.alpha.application.handler.AlphaHandler;

/** Interdit : un contrôleur qui appelle la classe concrète d'un handler. */
public class ControllerUsingHandler {
    void call(AlphaHandler handler) {
        handler.run();
    }
}
