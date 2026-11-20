package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;

/**
 * AUTH-32 : création du compte d'une inscription OAuth, demandée par le seul contexte {@code authentication}, qui lie
 * ensuite l'identité du fournisseur dans la même transaction (ADR 0008, ADR 0032). Appelé depuis un autre contexte : la
 * signature ne porte que des types de la JDK et {@link UserId}.
 */
public interface CreateOAuthAccountUseCase {

    /**
     * Le compte, actif si le fournisseur garantit le courriel, en attente sinon, avec un code SEAL envoyé (ADR 0032,
     * point 1). Mêmes refus qu'à l'inscription, l'identifiant d'abord (invalide, réservé, pris : AUTH-01 à AUTH-03),
     * puis le nom affiché (ACC-03, {@code VALIDATION_FAILED}) ; l'inscription juge le nom affiché en premier, dans sa
     * requête. Un courriel vérifié déjà pris par un compte actif donne {@code OAUTH_EMAIL_IN_USE} (AUTH-33). Appelé
     * seulement dans une transaction ouverte, qui lie aussitôt l'identité.
     *
     * @param email courriel du fournisseur, déjà au format attendu
     */
    UserId create(String username, String displayName, String email, boolean emailVerified);
}
