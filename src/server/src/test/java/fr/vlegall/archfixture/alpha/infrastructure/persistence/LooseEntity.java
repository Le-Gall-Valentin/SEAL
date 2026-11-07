package fr.vlegall.archfixture.alpha.infrastructure.persistence;

import jakarta.persistence.Entity;

/** Interdit : une entité hors de persistence/entity. */
@Entity
public class LooseEntity {}
