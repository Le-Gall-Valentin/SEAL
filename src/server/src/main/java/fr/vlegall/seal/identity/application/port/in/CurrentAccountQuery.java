package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.AccountView;

/** Compte de la session, relu en base (ADR 0019, point 5). */
public interface CurrentAccountQuery {

    AccountView current(UserId user);
}
