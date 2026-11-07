package fr.vlegall.seal;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Règles d'architecture des ADR 0001 et 0008 (mise à jour d'après NIDO), vérifiées à chaque build. */
class ArchitectureTest {

    private static final String ROOT = "fr.vlegall.seal";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private static final DescribedPredicate<JavaClass> JCA_PRIMITIVES = resideInAnyPackage(
                    "javax.crypto..", "java.security.spec..", "java.security.interfaces..")
            .or(belongToAnyOf(
                    Signature.class,
                    MessageDigest.class,
                    KeyFactory.class,
                    KeyPairGenerator.class,
                    KeyPair.class,
                    PrivateKey.class,
                    PublicKey.class))
            .as("des primitives de la JCA");

    @Test
    @DisplayName("ADR 0001 : seul le package crypto manipule la JCA")
    void onlyTheCryptoPackageUsesTheJca() {
        noClasses()
                .that()
                .resideOutsideOfPackage("fr.vlegall.seal.crypto..")
                .should()
                .dependOnClassesThat(JCA_PRIMITIVES)
                .because("toute primitive cryptographique passe par fr.vlegall.seal.crypto (ADR 0001)")
                .check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règles 1 et 2 : couches du domaine et de l'application")
    void layersPointDownwards() {
        ContextRules.domainIgnoresFrameworks(ROOT).check(CLASSES);
        ContextRules.domainDependsOnNothingAbove(ROOT).check(CLASSES);
        ContextRules.applicationIgnoresInfrastructure(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName(
            "ADR 0008, règle 3 : l'application ne dépend de Spring que pour les transactions, par @ApplicationService")
    void applicationLayerConventions() {
        ContextRules.applicationUsesSpringOnlyForTransactions(ROOT).check(CLASSES);
        ContextRules.applicationServicesLiveInHandlers(ROOT).check(CLASSES);
        ContextRules.handlersAreApplicationServices(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 4 : ports en interfaces, adaptateurs qui implémentent un port")
    void portsAndAdapters() {
        ContextRules.inboundPortsAreInterfaces(ROOT).check(CLASSES);
        ContextRules.outboundPortsAreInterfaces(ROOT).check(CLASSES);
        ContextRules.adaptersImplementAnOutboundPort(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règles 5 et 6 : couche web et entités JPA")
    void webAndPersistence() {
        ContextRules.controllersUseOnlyUseCases(ROOT).check(CLASSES);
        ContextRules.entitiesResideInPersistenceEntity(ROOT).check(CLASSES);
        ContextRules.onlyInfrastructureUsesEntities(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 7 : les contextes ne se parlent que par leurs cas d'usage")
    void contextsTalkThroughInboundPorts() {
        ContextRules.contextsTalkThroughInboundPorts(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 8 : packages transverses indépendants des contextes, crypto en Java pur")
    void transversePackages() {
        ContextRules.onlyInfrastructureUsesSecurity(ROOT).check(CLASSES);
        ContextRules.transversePackagesIgnoreContexts(ROOT).check(CLASSES);
        ContextRules.cryptoDependsOnlyOnTheJdk(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 9 : aucun cycle entre packages de premier niveau")
    void noCycles() {
        ContextRules.firstLevelPackagesHaveNoCycles(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 10 : horloge injectée")
    void clockIsInjected() {
        ContextRules.clockIsInjected(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 11 : @ConfigurationProperties dans common")
    void configurationPropertiesLiveInCommon() {
        ContextRules.configurationPropertiesLiveInCommon(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008, règle 12 : seul le contexte authentication manipule les mots de passe")
    void onlyAuthenticationHandlesPasswords() {
        ContextRules.onlyAuthenticationHandlesPasswords(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("TRN-02, ADR 0008, règle 13 : aucune ouverture CORS")
    void noCors() {
        ContextRules.noCors(ROOT).check(CLASSES);
    }
}
