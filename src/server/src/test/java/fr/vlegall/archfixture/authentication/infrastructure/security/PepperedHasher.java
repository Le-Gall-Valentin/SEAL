package fr.vlegall.archfixture.authentication.infrastructure.security;

import fr.vlegall.archfixture.common.PasswordPepper;

/** Permis : le hacheur d'authentication applique le poivre. */
public class PepperedHasher {
    int length(PasswordPepper pepper) {
        return pepper.value().length;
    }
}
