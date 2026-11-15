package fr.vlegall.archfixture.beta.infrastructure.identity;

import fr.vlegall.archfixture.identity.application.port.in.AccountLookupQuery;

/** Interdit : un autre contexte qu'authentication lit les coordonnées d'un compte. */
public class ContactAdapter {
    String emailOf(AccountLookupQuery.Contact contact) {
        return contact.email();
    }
}
