package fr.vlegall.archfixture.alpha.application.handler;

import fr.vlegall.archfixture.alpha.application.port.in.AlphaUseCase;
import fr.vlegall.archfixture.alpha.domain.port.out.AlphaStore;
import fr.vlegall.seal.common.ApplicationService;
import org.springframework.transaction.annotation.Transactional;

/** Permis : un handler annoté, qui ne dépend de Spring que pour les transactions. */
@ApplicationService
public class AlphaHandler implements AlphaUseCase {
    private final AlphaStore store;

    public AlphaHandler(AlphaStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void run() {
        store.save();
    }
}
