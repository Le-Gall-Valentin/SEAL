package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;

/**
 * API-02 : un lien qui autorise {@code caller} à lire les clés de {@code owner} : l'amitié (contexte {@code friends}) ou
 * un groupe commun (contexte {@code conversations}, ADR 0039, point 7). Un seul lien suffit.
 */
@FunctionalInterface
public interface RelationPort {

    boolean related(UserId caller, UserId owner);
}
