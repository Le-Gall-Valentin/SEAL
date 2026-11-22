package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.common.UserId;

/** ADR 0035, point 2 : méthodes de connexion du compte ({@code GET /api/v1/account/identities}). */
public interface LoginMethodsQuery {

    LoginMethods of(UserId user);
}
