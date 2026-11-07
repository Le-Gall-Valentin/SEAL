package fr.vlegall.archfixture.alpha.infrastructure.web;

import fr.vlegall.archfixture.alpha.infrastructure.persistence.AlphaJpaRepository;

/** Interdit : un contrôleur qui appelle un dépôt au lieu d'un cas d'usage. */
public class ControllerUsingRepository {
    Object read(AlphaJpaRepository repository) {
        return repository.find();
    }
}
