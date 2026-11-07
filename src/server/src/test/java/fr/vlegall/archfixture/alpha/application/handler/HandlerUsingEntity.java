package fr.vlegall.archfixture.alpha.application.handler;

import fr.vlegall.archfixture.alpha.infrastructure.persistence.AlphaEntity;

/** Interdit : une entité JPA utilisée hors de infrastructure. */
public class HandlerUsingEntity {
    Object load() {
        return new AlphaEntity();
    }
}
