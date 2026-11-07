package fr.vlegall.seal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Optional;
import java.util.Set;

/**
 * Règles de l'ADR 0008, construites pour un package racine : appliquées au code de SEAL par {@link ArchitectureTest},
 * et aux contextes témoins de {@code fr.vlegall.archfixture} par {@link ContextRulesTest}, qui prouve qu'elles
 * attrapent leurs contre-exemples. Un contexte est un sous-package direct de la racine, hors packages techniques.
 */
final class ContextRules {

    private static final Set<String> TECHNICAL_PACKAGES = Set.of("common", "security", "crypto");

    private ContextRules() {}

    /** Un contexte n'en atteint un autre que depuis son {@code infrastructure}, vers son {@code application.port.in}. */
    static ArchRule contextsTalkThroughInboundPorts(String root) {
        return classes()
                .that(belongToAContext(root))
                .should(onlyReachOtherContextsThroughInboundPorts(root))
                .allowEmptyShould(true);
    }

    /** Un contrôleur appelle des cas d'usage, jamais un dépôt ni un port sortant. */
    static ArchRule controllersUseOnlyUseCases(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..infrastructure.web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(root + "..infrastructure.persistence..", root + "..domain.port.out..")
                .allowEmptyShould(true);
    }

    static ArchRule entitiesResideInPersistence(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAnnotatedWith(Entity.class)
                .should()
                .resideInAPackage(root + "..infrastructure.persistence..")
                .allowEmptyShould(true);
    }

    static ArchRule onlyInfrastructureUsesEntities(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(root + "..infrastructure..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .allowEmptyShould(true);
    }

    /** Le hachage des mots de passe reste dans le contexte {@code authentication} (ADR 0008). */
    static ArchRule onlyAuthenticationHandlesPasswords(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(root + ".authentication..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        // Interface et délégation, puis chaque famille d'encodeurs : un appel direct à
                        // Argon2PasswordEncoder ne dépend que de son propre package.
                        "org.springframework.security.crypto.password..",
                        "org.springframework.security.crypto.factory..",
                        "org.springframework.security.crypto.argon2..",
                        "org.springframework.security.crypto.bcrypt..",
                        "org.springframework.security.crypto.scrypt..")
                .allowEmptyShould(true);
    }

    private static DescribedPredicate<JavaClass> belongToAContext(String root) {
        return DescribedPredicate.describe(
                "appartiennent à un contexte de " + root,
                javaClass -> contextOf(root, javaClass.getPackageName()).isPresent());
    }

    private static ArchCondition<JavaClass> onlyReachOtherContextsThroughInboundPorts(String root) {
        return new ArchCondition<>(
                "n'atteindre un autre contexte que par son application.port.in, depuis infrastructure") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = contextOf(root, origin.getPackageName()).orElseThrow();
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    Optional<String> to = contextOf(root, target.getPackageName());
                    if (to.isEmpty() || to.get().equals(from)) {
                        continue;
                    }
                    boolean allowed = inPackage(origin.getPackageName(), root + "." + from + ".infrastructure")
                            && inPackage(target.getPackageName(), root + "." + to.get() + ".application.port.in");
                    if (!allowed) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static Optional<String> contextOf(String root, String packageName) {
        if (!packageName.startsWith(root + ".")) {
            return Optional.empty();
        }
        String rest = packageName.substring(root.length() + 1);
        int dot = rest.indexOf('.');
        String first = dot < 0 ? rest : rest.substring(0, dot);
        return TECHNICAL_PACKAGES.contains(first) ? Optional.empty() : Optional.of(first);
    }

    private static boolean inPackage(String packageName, String base) {
        return packageName.equals(base) || packageName.startsWith(base + ".");
    }
}
