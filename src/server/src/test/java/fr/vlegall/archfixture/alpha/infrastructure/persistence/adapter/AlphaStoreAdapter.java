package fr.vlegall.archfixture.alpha.infrastructure.persistence.adapter;

import fr.vlegall.archfixture.alpha.domain.port.out.AlphaStore;
import org.springframework.stereotype.Component;

/** Permis : un adaptateur qui implémente un port sortant. */
@Component
public class AlphaStoreAdapter implements AlphaStore {
    @Override
    public void save() {}
}
