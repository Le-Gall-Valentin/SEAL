package fr.vlegall.archfixture.alpha.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Interdit : des propriétés de configuration hors de common. */
@ConfigurationProperties("alpha")
public record AlphaProperties(String value) {}
