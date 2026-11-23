package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;

/**
 * API-02 : lien qui autorise {@code caller} à lire les clés de {@code owner} : l'amitié, demandée au contexte
 * {@code friends} ; la PR 3 du lot 3 y ajoute la conversation commune.
 */
@FunctionalInterface
public interface RelationPort {

    boolean related(UserId caller, UserId owner);
}
