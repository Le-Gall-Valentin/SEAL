package fr.vlegall.archfixture.identity.application.port.in;

/** Port d'identity qui livre des coordonnées personnelles, réservé au contexte authentication. */
public interface AccountLookupQuery {

    interface Contact {
        String email();
    }

    Contact findActiveContact(String user);
}
