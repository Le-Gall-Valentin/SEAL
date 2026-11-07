package fr.vlegall.seal.security;

import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.security.session.AbsoluteSessionTimeoutFilter;
import fr.vlegall.seal.security.web.ProblemDetailsSecurityHandler;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Chaîne de filtres de Spring Security : tout est refusé par défaut et ouvert route par route (A01), et chaque réponse
 * porte les en-têtes de la section 12.3 du CDC (TRN-20).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    /** TRN-20, TRN-21 : aucune ressource tierce, aucun script ni style en ligne. */
    static String contentSecurityPolicy(SealProperties seal) {
        return "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; "
                + "font-src 'self'; connect-src 'self' " + seal.webSocketOrigin() + "; manifest-src 'self'; "
                + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'";
    }

    /** TRN-20 : en-têtes de la section 12.3 sur les réponses du dispatch d'erreur (ADR 0014, point 1). */
    @Bean
    FilterRegistrationBean<ErrorDispatchHeadersFilter> errorDispatchHeadersFilter(SealProperties seal) {
        FilterRegistrationBean<ErrorDispatchHeadersFilter> registration =
                new FilterRegistrationBean<>(new ErrorDispatchHeadersFilter(seal));
        registration.setDispatcherTypes(DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, SealProperties seal, ProblemDetailsSecurityHandler problemDetails, Clock clock) {
        http.authorizeHttpRequests(requests -> requests
                        // Le client React compilé, servi par Spring sur l'origine unique (TRN-02).
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.svg")
                        .permitAll()
                        // AUTH-60 : jeton CSRF rechargé par le client.
                        .requestMatchers(HttpMethod.GET, "/api/v1/csrf")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        // Sonde de santé du conteneur, sur le port de gestion interne (A02).
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                // AUTH-60 : cookie XSRF-TOKEN lisible par le client, renvoyé dans l'en-tête X-XSRF-TOKEN.
                .csrf(csrf -> csrf.spa())
                // QA-05 : 401 sans session, 403 sans jeton CSRF ou sans droit, en Problem Details.
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problemDetails).accessDeniedHandler(problemDetails))
                // Une requête anonyme refusée ne doit pas créer de session pour mémoriser son URL : sinon n'importe
                // qui remplirait la table des sessions.
                .requestCache(RequestCacheConfigurer::disable)
                // A01 : pas de /logout implicite ; la déconnexion sera POST /api/v1/auth/logout (AUTH-53).
                .logout(LogoutConfigurer::disable)
                // AUTH-52 : la session de plus de 12 heures est invalidée avant de charger le contexte de sécurité.
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(clock), SecurityContextHolderFilter.class)
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives(contentSecurityPolicy(seal)))
                        // TRN-03 : sans includeSubDomains, pour ne pas imposer HTTPS aux autres services locaux.
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(false)
                                .maxAgeInSeconds(seal.security().hstsMaxAge().toSeconds()))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }
}
