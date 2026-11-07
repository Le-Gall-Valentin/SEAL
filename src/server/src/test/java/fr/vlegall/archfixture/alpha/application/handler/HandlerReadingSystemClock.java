package fr.vlegall.archfixture.alpha.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import java.time.Instant;

/** Interdit : une lecture de l'horloge système au lieu de l'horloge injectée. */
@ApplicationService
public class HandlerReadingSystemClock {
    Instant now() {
        return Instant.now();
    }
}
