package fr.vlegall.seal.authentication.infrastructure.security;

import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * A10 : appels aux fournisseurs OAuth bornés dans le temps. Sans délai, un fournisseur muet retiendrait un fil du
 * serveur indéfiniment pendant un retour OAuth.
 */
final class ProviderHttp {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private ProviderHttp() {}

    static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /** Le {@code RestTemplate} des services de Spring Security, avec ces délais et sa gestion des erreurs OAuth. */
    static RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate(requestFactory());
        template.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        return template;
    }
}
