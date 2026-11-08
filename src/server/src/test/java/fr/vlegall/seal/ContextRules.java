package fr.vlegall.seal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import fr.vlegall.seal.common.ApplicationService;
import jakarta.persistence.Entity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;

/**
 * Règles de l'ADR 0008 (mise à jour d'après NIDO), construites pour un package racine : appliquées au code de SEAL par
 * {@link ArchitectureTest}, et aux contextes témoins de {@code fr.vlegall.archfixture} par {@link ContextRulesTest},
 * qui prouve que chacune attrape ses contre-exemples. Un contexte est un sous-package direct de la racine, hors
 * packages techniques.
 */
final class ContextRules {

    private static final Set<String> TECHNICAL_PACKAGES = Set.of("common", "security", "crypto");

    private ContextRules() {}

    // Règle 1
    static ArchRule domainIgnoresFrameworks(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..");
    }

    // Règle 2
    static ArchRule domainDependsOnNothingAbove(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(root + "..application..", root + "..infrastructure..");
    }

    static ArchRule applicationIgnoresInfrastructure(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(root + "..infrastructure..");
    }

    // Règle 3
    static ArchRule applicationUsesSpringOnlyForTransactions(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..application..")
                .should()
                .dependOnClassesThat(DescribedPredicate.describe(
                        "appartiennent à Spring, hors org.springframework.transaction",
                        javaClass -> javaClass.getPackageName().startsWith("org.springframework.")
                                && !javaClass.getPackageName().startsWith("org.springframework.transaction")));
    }

    static ArchRule applicationServicesLiveInHandlers(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAnnotatedWith(ApplicationService.class)
                .should()
                .resideInAPackage(root + "..application.handler..");
    }

    static ArchRule handlersAreApplicationServices(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..application.handler..")
                .and()
                .haveSimpleNameEndingWith("Handler")
                .should()
                .beAnnotatedWith(ApplicationService.class);
    }

    // Règle 4
    static ArchRule inboundPortsAreInterfaces(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..application.port.in..")
                .should()
                .beInterfaces();
    }

    static ArchRule outboundPortsAreInterfaces(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..domain.port.out..")
                .should()
                .beInterfaces();
    }

    static ArchRule adaptersImplementAnOutboundPort(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..infrastructure..")
                .and()
                .haveSimpleNameEndingWith("Adapter")
                .and()
                .areMetaAnnotatedWith(Component.class)
                .should()
                .implement(DescribedPredicate.describe(
                        "un port de domain/port/out",
                        javaInterface -> javaInterface.getPackageName().contains(".domain.port.out")));
    }

    // Règle 5
    static ArchRule controllersUseOnlyUseCases(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..infrastructure.web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        root + "..application.handler..",
                        root + "..infrastructure.persistence..",
                        root + "..domain.port.out..");
    }

    // Règle 6
    static ArchRule entitiesResideInPersistenceEntity(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAnnotatedWith(Entity.class)
                .should()
                .resideInAPackage(root + "..infrastructure.persistence.entity..");
    }

    static ArchRule onlyInfrastructureUsesEntities(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(root + "..infrastructure..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class);
    }

    // Règle 7
    static ArchRule contextsTalkThroughInboundPorts(String root) {
        return classes().that(belongToAContext(root)).should(onlyReachOtherContextsThroughInboundPorts(root));
    }

    // Règle 8
    static ArchRule onlyInfrastructureUsesSecurity(String root) {
        return noClasses()
                .that(belongToAContext(root))
                .and()
                .resideOutsideOfPackage(root + "..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(root + ".security..");
    }

    static ArchRule transversePackagesIgnoreContexts(String root) {
        return noClasses()
                .that()
                .resideInAnyPackage(root + ".common..", root + ".security..", root + ".crypto..")
                .should()
                .dependOnClassesThat(belongToAContext(root));
    }

    static ArchRule cryptoDependsOnlyOnTheJdk(String root) {
        return classes()
                .that()
                .resideInAPackage(root + ".crypto..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(root + ".crypto..", "java..", "javax..");
    }

    // Règle 9
    static ArchRule firstLevelPackagesHaveNoCycles(String root) {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles();
    }

    // Règle 10
    static ArchRule clockIsInjected(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .doNotHaveFullyQualifiedName(root + ".common.TimeConfiguration")
                .should()
                .callMethod(Instant.class, "now")
                .orShould()
                .callMethod(LocalDate.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now")
                .orShould()
                .callMethod(ZonedDateTime.class, "now")
                .orShould()
                .callMethod(OffsetDateTime.class, "now")
                .orShould()
                .callMethod(System.class, "currentTimeMillis")
                .orShould()
                .callMethod(Clock.class, "systemUTC")
                .orShould()
                .callMethod(Clock.class, "systemDefaultZone");
    }

    // Règle 11
    static ArchRule configurationPropertiesLiveInCommon(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .areAnnotatedWith(ConfigurationProperties.class)
                .should()
                .resideInAPackage(root + ".common..");
    }

    // Règle 12
    static ArchRule onlyAuthenticationHandlesPasswords(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(root + ".authentication..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        // Interface et délégation, puis chaque famille d'encodeurs : un appel direct à un encodeur
                        // ne dépend que de son propre package.
                        "org.springframework.security.crypto.password..",
                        "org.springframework.security.crypto.factory..",
                        "org.springframework.security.crypto.argon2..",
                        "org.springframework.security.crypto.bcrypt..",
                        "org.springframework.security.crypto.scrypt..",
                        "org.springframework.security.crypto.password4j..");
    }

    // Règle 12, complétée par l'ADR 0018
    static ArchRule noDirectBouncyCastle(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.bouncycastle..")
                .because("seul Argon2PasswordEncoder de Spring Security se sert de Bouncy Castle (ADR 0018)");
    }

    // Règle 14
    static ArchRule onlyCommonMailSendsMail(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(root + ".common.mail..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.mail..", "jakarta.mail..")
                .because("un courriel part après la validation de la transaction, par MailDispatcher (ADR 0019)");
    }

    // Règle 13
    static ArchRule noCors(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.web.cors..")
                .orShould()
                .dependOnClassesThat()
                .belongToAnyOf(CrossOrigin.class);
    }

    private static DescribedPredicate<JavaClass> belongToAContext(String root) {
        return DescribedPredicate.describe(
                "appartiennent à un contexte de " + root,
                javaClass -> contextOf(root, javaClass.getPackageName()).isPresent());
    }

    private static ArchCondition<JavaClass> onlyReachOtherContextsThroughInboundPorts(String root) {
        return new ArchCondition<>(
                "n'atteindre un autre contexte que depuis infrastructure/<autre contexte>, vers son application.port.in") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = contextOf(root, origin.getPackageName()).orElseThrow();
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    Optional<String> to = contextOf(root, target.getPackageName());
                    if (to.isEmpty() || to.get().equals(from)) {
                        continue;
                    }
                    boolean allowed = inPackage(
                                    origin.getPackageName(), root + "." + from + ".infrastructure." + to.get())
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
