package fr.vlegall.seal.authentication.infrastructure.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.converter.ClaimTypeConverter;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Jetons d'identité OIDC (AUTH-30) : la vérification de l'{@code OidcIdTokenDecoderFactory} de Spring (RS256, émetteur,
 * audience, dates, conversion des déclarations), avec des JWKS lus en temps borné (A10). Un décodeur par fournisseur,
 * qui garde ses clés en cache.
 */
final class IdTokenDecoders implements JwtDecoderFactory<ClientRegistration> {

    private static final ClaimTypeConverter CLAIM_TYPES =
            new ClaimTypeConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters());

    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    @Override
    public JwtDecoder createDecoder(ClientRegistration registration) {
        return decoders.computeIfAbsent(registration.getRegistrationId(), id -> build(registration));
    }

    private static JwtDecoder build(ClientRegistration registration) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                        registration.getProviderDetails().getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(ProviderHttp.requestFactory()))
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithValidators(List.of(new OidcIdTokenValidator(registration))));
        decoder.setClaimSetConverter(CLAIM_TYPES);
        return decoder;
    }
}
