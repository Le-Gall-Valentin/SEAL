package fr.vlegall.seal.security.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Paramètre de contrôleur qui reçoit le {@code UserId} de la session (d'après NIDO). La route doit exiger une session :
 * les règles d'URL de {@code SecurityConfig} refusent l'anonyme avant d'atteindre le contrôleur.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "userId", errorOnInvalidType = true)
public @interface CurrentUser {}
