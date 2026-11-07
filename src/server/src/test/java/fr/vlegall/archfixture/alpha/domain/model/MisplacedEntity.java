package fr.vlegall.archfixture.alpha.domain.model;

import jakarta.persistence.Entity;

/** Interdit : une entité JPA hors de infrastructure.persistence. */
@Entity
public class MisplacedEntity {}
