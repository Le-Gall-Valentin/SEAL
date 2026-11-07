package fr.vlegall.archfixture.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Permis : des propriétés de configuration dans common. */
@ConfigurationProperties("fixture")
public record FixtureProperties(String value) {}
