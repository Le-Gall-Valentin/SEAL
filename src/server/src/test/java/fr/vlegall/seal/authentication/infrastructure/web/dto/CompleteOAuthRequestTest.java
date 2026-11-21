package fr.vlegall.seal.authentication.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-01, QA-05 : bornes de la requête de fin d'inscription OAuth, celles de l'inscription (AUTH-32). */
@DisplayName("AUTH-32 : bornes de POST /api/v1/auth/oauth/complete")
class CompleteOAuthRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    @DisplayName("SEC-01 : 64 caractères d'identifiant et 200 de nom passent la requête ; un de plus est refusé")
    void sec01Bounds() {
        assertThat(validator.validate(new CompleteOAuthRequest("u".repeat(64), "n".repeat(200))))
                .isEmpty();
        assertThat(validator.validate(new CompleteOAuthRequest("u".repeat(65), "Nom")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("username");
        assertThat(validator.validate(new CompleteOAuthRequest("nom", "n".repeat(201))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("displayName");
    }

    @Test
    @DisplayName("SEC-01 : identifiant et nom sont obligatoires")
    void sec01Required() {
        assertThat(validator.validate(new CompleteOAuthRequest(null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("username", "displayName");
    }
}
