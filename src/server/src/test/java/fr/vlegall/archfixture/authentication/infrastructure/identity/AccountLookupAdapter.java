package fr.vlegall.archfixture.authentication.infrastructure.identity;

import fr.vlegall.archfixture.identity.application.port.in.AccountLookupQuery;

/** Permis : authentication écrit au titulaire, et lui seul a besoin de ses coordonnées. */
public class AccountLookupAdapter {
    String emailOf(AccountLookupQuery accounts, String user) {
        return accounts.findActiveContact(user).email();
    }
}
