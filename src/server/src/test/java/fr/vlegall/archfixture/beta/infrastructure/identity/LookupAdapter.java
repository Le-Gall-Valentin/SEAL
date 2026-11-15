package fr.vlegall.archfixture.beta.infrastructure.identity;

import fr.vlegall.archfixture.identity.application.port.in.AccountLookupQuery;

/** Interdit : un autre contexte qu'authentication passe par le port des coordonnées. */
public class LookupAdapter {
    Object find(AccountLookupQuery accounts, String user) {
        return accounts.findActiveContact(user);
    }
}
