package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import fr.vlegall.archfixture.alpha.infrastructure.beta.BetaAdapter;
import fr.vlegall.archfixture.alpha.infrastructure.persistence.AlphaEntity;
import fr.vlegall.archfixture.alpha.infrastructure.persistence.AlphaJpaRepository;
import fr.vlegall.archfixture.authentication.infrastructure.HashingAdapter;
import fr.vlegall.archfixture.beta.application.port.in.BetaUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les règles de l'ADR 0008 attrapent bien ce qu'elles doivent attraper : appliquées aux contextes témoins de
 * {@code fr.vlegall.archfixture}, chaque règle signale ses contre-exemples et laisse passer les cas permis.
 */
class ContextRulesTest {

    private static final String ROOT = "fr.vlegall.archfixture";
    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(ROOT);

    private static JavaClasses only(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    @Test
    @DisplayName("ADR 0008 : un contexte n'atteint un autre que par son application.port.in, depuis son infrastructure")
    void crossContextDependenciesGoThroughInboundPorts() {
        assertThatThrownBy(
                        () -> ContextRules.contextsTalkThroughInboundPorts(ROOT).check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HandlerCallingBeta")
                .hasMessageContaining("AdapterReachingBetaDomain")
                .hasMessageNotContaining("infrastructure.beta.BetaAdapter");
        assertThatCode(() -> ContextRules.contextsTalkThroughInboundPorts(ROOT)
                        .check(only(BetaAdapter.class, BetaUseCase.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADR 0008 : un contrôleur n'appelle jamais un dépôt")
    void controllersDoNotUseRepositories() {
        assertThatThrownBy(() -> ContextRules.controllersUseOnlyUseCases(ROOT).check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ControllerUsingRepository");
    }

    @Test
    @DisplayName("ADR 0008 : une entité JPA réside dans infrastructure.persistence")
    void entitiesLiveInPersistence() {
        assertThatThrownBy(() -> ContextRules.entitiesResideInPersistence(ROOT).check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MisplacedEntity")
                .hasMessageNotContaining("persistence.AlphaEntity");
    }

    @Test
    @DisplayName("ADR 0008 : seule l'infrastructure dépend des entités JPA")
    void onlyInfrastructureUsesEntities() {
        assertThatThrownBy(
                        () -> ContextRules.onlyInfrastructureUsesEntities(ROOT).check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HandlerUsingEntity");
        assertThatCode(() -> ContextRules.onlyInfrastructureUsesEntities(ROOT)
                        .check(only(AlphaEntity.class, AlphaJpaRepository.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADR 0008 : seul le contexte authentication manipule les mots de passe")
    void onlyAuthenticationUsesPasswordEncoders() {
        assertThatThrownBy(() ->
                        ContextRules.onlyAuthenticationHandlesPasswords(ROOT).check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PasswordAdapter")
                .hasMessageContaining("Argon2Adapter")
                .hasMessageNotContaining("HashingAdapter");
        assertThatCode(() ->
                        ContextRules.onlyAuthenticationHandlesPasswords(ROOT).check(only(HashingAdapter.class)))
                .doesNotThrowAnyException();
    }
}
