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

/** Règles d'architecture des ADR 0001 et 0008, vérifiées à chaque build. */
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
    @DisplayName("ADR 0001 : le domaine ignore Spring, JPA et Hibernate")
    void theDomainIgnoresFrameworks() {
        noClasses()
                .that()
                .resideInAPackage("fr.vlegall.seal..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0001 : le domaine ne dépend ni de l'application ni de l'infrastructure")
    void theDomainDependsOnNothingAboveIt() {
        noClasses()
                .that()
                .resideInAPackage("fr.vlegall.seal..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("fr.vlegall.seal..application..", "fr.vlegall.seal..infrastructure..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0001 : l'application ne dépend pas de l'infrastructure")
    void theApplicationDoesNotDependOnInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("fr.vlegall.seal..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("fr.vlegall.seal..infrastructure..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008 : les contextes ne se parlent que par leurs cas d'usage")
    void contextsTalkThroughInboundPorts() {
        ContextRules.contextsTalkThroughInboundPorts(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008 : un contrôleur appelle des cas d'usage, jamais un dépôt")
    void controllersUseOnlyUseCases() {
        ContextRules.controllersUseOnlyUseCases(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008 : les entités JPA restent dans l'infrastructure")
    void entitiesStayInInfrastructure() {
        ContextRules.entitiesResideInPersistence(ROOT).check(CLASSES);
        ContextRules.onlyInfrastructureUsesEntities(ROOT).check(CLASSES);
    }

    @Test
    @DisplayName("ADR 0008 : seul le contexte authentication manipule les mots de passe")
    void onlyAuthenticationHandlesPasswords() {
        ContextRules.onlyAuthenticationHandlesPasswords(ROOT).check(CLASSES);
    }
}
