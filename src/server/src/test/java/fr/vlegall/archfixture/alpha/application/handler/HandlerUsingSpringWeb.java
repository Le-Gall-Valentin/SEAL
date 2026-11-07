package fr.vlegall.archfixture.alpha.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import org.springframework.web.client.RestClient;

/** Interdit : une application qui dépend de Spring au-delà des transactions. */
@ApplicationService
public class HandlerUsingSpringWeb {
    Object call(RestClient client) {
        return client.get();
    }
}
