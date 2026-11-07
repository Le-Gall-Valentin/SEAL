package fr.vlegall.seal.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Service;

/**
 * Marque l'implémentation d'un cas d'usage, dans {@code application/handler} (ADR 0008, d'après NIDO). Elle porte
 * {@link Service} : la couche {@code application} ne dépend ainsi de Spring que pour les transactions.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface ApplicationService {}
