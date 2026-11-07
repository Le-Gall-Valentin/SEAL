package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import fr.vlegall.archfixture.alpha.application.handler.AlphaHandler;
import fr.vlegall.archfixture.alpha.application.port.in.AlphaUseCase;
import fr.vlegall.archfixture.alpha.domain.model.AlphaThing;
import fr.vlegall.archfixture.alpha.domain.port.out.AlphaStore;
import fr.vlegall.archfixture.alpha.infrastructure.beta.BetaAdapter;
import fr.vlegall.archfixture.alpha.infrastructure.journal.JournalAdapter;
import fr.vlegall.archfixture.alpha.infrastructure.persistence.adapter.AlphaStoreAdapter;
import fr.vlegall.archfixture.alpha.infrastructure.persistence.entity.AlphaEntity;
import fr.vlegall.archfixture.alpha.infrastructure.persistence.repository.AlphaJpaRepository;
import fr.vlegall.archfixture.alpha.infrastructure.web.ControllerUsingUseCase;
import fr.vlegall.archfixture.authentication.infrastructure.HashingAdapter;
import fr.vlegall.archfixture.beta.application.port.in.BetaUseCase;
import fr.vlegall.archfixture.common.FixtureProperties;
import fr.vlegall.archfixture.common.TimeConfiguration;
import fr.vlegall.archfixture.crypto.PureCrypto;
import fr.vlegall.archfixture.security.FixtureJournal;
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

    private static void expectViolations(ArchRule rule, String... offenders) {
        var assertion = assertThatThrownBy(() -> rule.check(FIXTURES)).isInstanceOf(AssertionError.class);
        for (String offender : offenders) {
            assertion.hasMessageContaining(offender);
        }
    }

    private static void expectAllowed(ArchRule rule, Class<?>... classes) {
        assertThatCode(() -> rule.check(only(classes))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADR 0008, règle 1 : le domaine ignore Spring, JPA, Hibernate, Jackson et jakarta")
    void rule1DomainIgnoresFrameworks() {
        expectViolations(ContextRules.domainIgnoresFrameworks(ROOT), "JacksonInDomain", "MisplacedEntity");
        expectAllowed(ContextRules.domainIgnoresFrameworks(ROOT), AlphaThing.class);
    }

    @Test
    @DisplayName(
            "ADR 0008, règle 2 : le domaine ne dépend de rien au-dessus de lui, l'application pas de l'infrastructure")
    void rule2LayersPointDownwards() {
        expectViolations(ContextRules.domainDependsOnNothingAbove(ROOT), "DomainUsingApplication");
        expectViolations(ContextRules.applicationIgnoresInfrastructure(ROOT), "HandlerUsingEntity");
        expectAllowed(ContextRules.applicationIgnoresInfrastructure(ROOT), AlphaHandler.class, AlphaStore.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 3 : l'application ne dépend de Spring que pour les transactions")
    void rule3ApplicationUsesSpringOnlyForTransactions() {
        expectViolations(ContextRules.applicationUsesSpringOnlyForTransactions(ROOT), "HandlerUsingSpringWeb");
        expectAllowed(ContextRules.applicationUsesSpringOnlyForTransactions(ROOT), AlphaHandler.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 3 : un handler porte @ApplicationService, et seul un handler la porte")
    void rule3HandlersAreApplicationServices() {
        expectViolations(ContextRules.applicationServicesLiveInHandlers(ROOT), "MisplacedApplicationService");
        expectViolations(ContextRules.handlersAreApplicationServices(ROOT), "UnannotatedHandler");
        expectAllowed(ContextRules.applicationServicesLiveInHandlers(ROOT), AlphaHandler.class);
        expectAllowed(ContextRules.handlersAreApplicationServices(ROOT), AlphaHandler.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 4 : les ports sont des interfaces et tout adaptateur implémente un port sortant")
    void rule4PortsAndAdapters() {
        expectViolations(ContextRules.inboundPortsAreInterfaces(ROOT), "ConcreteUseCase");
        expectViolations(ContextRules.outboundPortsAreInterfaces(ROOT), "ConcreteStore");
        expectViolations(ContextRules.adaptersImplementAnOutboundPort(ROOT), "LooseAdapter");
        expectAllowed(ContextRules.inboundPortsAreInterfaces(ROOT), AlphaUseCase.class, BetaUseCase.class);
        expectAllowed(ContextRules.outboundPortsAreInterfaces(ROOT), AlphaStore.class);
        expectAllowed(ContextRules.adaptersImplementAnOutboundPort(ROOT), AlphaStoreAdapter.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 5 : la couche web n'appelle que des cas d'usage")
    void rule5ControllersUseOnlyUseCases() {
        expectViolations(
                ContextRules.controllersUseOnlyUseCases(ROOT), "ControllerUsingRepository", "ControllerUsingHandler");
        expectAllowed(ContextRules.controllersUseOnlyUseCases(ROOT), ControllerUsingUseCase.class);
    }

    @Test
    @DisplayName(
            "ADR 0008, règle 6 : les entités JPA résident dans persistence/entity et seule l'infrastructure en dépend")
    void rule6EntitiesStayInPersistence() {
        expectViolations(ContextRules.entitiesResideInPersistenceEntity(ROOT), "MisplacedEntity", "LooseEntity");
        expectViolations(ContextRules.onlyInfrastructureUsesEntities(ROOT), "HandlerUsingEntity");
        expectAllowed(ContextRules.entitiesResideInPersistenceEntity(ROOT), AlphaEntity.class);
        expectAllowed(ContextRules.onlyInfrastructureUsesEntities(ROOT), AlphaEntity.class, AlphaJpaRepository.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 7 : un contexte n'en atteint un autre que depuis infrastructure/<autre contexte>")
    void rule7ContextsTalkThroughInboundPorts() {
        expectViolations(
                ContextRules.contextsTalkThroughInboundPorts(ROOT),
                "HandlerCallingBeta",
                "AdapterReachingBetaDomain",
                "ControllerCallingBeta");
        expectAllowed(ContextRules.contextsTalkThroughInboundPorts(ROOT), BetaAdapter.class, BetaUseCase.class);
    }

    @Test
    @DisplayName(
            "ADR 0008, règle 8 : seule l'infrastructure utilise security, les packages transverses ignorent les contextes")
    void rule8TransversePackages() {
        expectViolations(ContextRules.onlyInfrastructureUsesSecurity(ROOT), "DomainUsingSecurity");
        expectViolations(ContextRules.transversePackagesIgnoreContexts(ROOT), "CommonUsingAlpha");
        expectViolations(ContextRules.cryptoDependsOnlyOnTheJdk(ROOT), "CryptoUsingSpring");
        expectAllowed(ContextRules.onlyInfrastructureUsesSecurity(ROOT), JournalAdapter.class, FixtureJournal.class);
        expectAllowed(
                ContextRules.transversePackagesIgnoreContexts(ROOT), TimeConfiguration.class, FixtureJournal.class);
        expectAllowed(ContextRules.cryptoDependsOnlyOnTheJdk(ROOT), PureCrypto.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 9 : aucun cycle entre packages de premier niveau")
    void rule9NoCycles() {
        expectViolations(ContextRules.firstLevelPackagesHaveNoCycles(ROOT), "CycleA");
        expectAllowed(ContextRules.firstLevelPackagesHaveNoCycles(ROOT), TimeConfiguration.class, FixtureJournal.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 10 : l'horloge système ne se lit que dans common.TimeConfiguration")
    void rule10ClockIsInjected() {
        expectViolations(ContextRules.clockIsInjected(ROOT), "HandlerReadingSystemClock");
        // AlphaThing entre dans le périmètre : une règle qui n'examine rien échoue, c'est le garde-fou contre le vide.
        expectAllowed(ContextRules.clockIsInjected(ROOT), TimeConfiguration.class, AlphaThing.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 11 : @ConfigurationProperties seulement dans common")
    void rule11ConfigurationPropertiesLiveInCommon() {
        expectViolations(ContextRules.configurationPropertiesLiveInCommon(ROOT), "AlphaProperties");
        expectAllowed(ContextRules.configurationPropertiesLiveInCommon(ROOT), FixtureProperties.class);
    }

    @Test
    @DisplayName("ADR 0008, règle 12 : seul le contexte authentication manipule les encodeurs de mots de passe")
    void rule12OnlyAuthenticationUsesPasswordEncoders() {
        expectViolations(
                ContextRules.onlyAuthenticationHandlesPasswords(ROOT),
                "PasswordAdapter",
                "Argon2Adapter",
                "Password4jAdapter");
        expectAllowed(ContextRules.onlyAuthenticationHandlesPasswords(ROOT), HashingAdapter.class, AlphaThing.class);
    }

    @Test
    @DisplayName("TRN-02, ADR 0008, règle 13 : aucune ouverture CORS")
    void rule13NoCors() {
        expectViolations(ContextRules.noCors(ROOT), "CrossOriginController", "CorsConfig");
        expectAllowed(ContextRules.noCors(ROOT), ControllerUsingUseCase.class);
    }
}
