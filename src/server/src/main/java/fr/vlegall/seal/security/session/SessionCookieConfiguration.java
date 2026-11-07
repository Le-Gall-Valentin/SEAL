package fr.vlegall.seal.security.session;

import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AUTH-51 : cookie de session {@code __Host-seal_sid}, {@code Secure}, {@code HttpOnly}, {@code SameSite=Lax},
 * {@code Path=/}, sans {@code Domain}. {@code Lax} et non {@code Strict} : le retour OAuth est une navigation
 * inter-sites qui doit porter le cookie.
 *
 * <p>Fixé dans le code plutôt que par {@code server.servlet.session.cookie.*}, que Spring Boot n'applique au cookie de
 * Spring Session qu'avec un serveur embarqué : les attributs sont ainsi les mêmes partout, tests compris.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfiguration {

    static final String COOKIE_NAME = "__Host-seal_sid";

    @Bean
    DefaultCookieSerializerCustomizer sessionCookie() {
        return serializer -> {
            serializer.setCookieName(COOKIE_NAME);
            serializer.setCookiePath("/");
            serializer.setUseSecureCookie(true);
            serializer.setUseHttpOnlyCookie(true);
            serializer.setSameSite("Lax");
            serializer.setDomainName(null);
        };
    }
}
