package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.common.UserId;

/**
 * ADR 0035, point 2 : méthodes de connexion du compte ({@code GET /api/v1/account/identities}) : tous les fournisseurs
 * liés, configurés ou non ; le client ne propose que les configurés (OPS-03).
 */
public interface LoginMethodsQuery {

    LoginMethods of(UserId user);
}
