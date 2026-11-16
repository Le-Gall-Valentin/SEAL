package fr.vlegall.archfixture.beta.infrastructure.security;

import fr.vlegall.archfixture.common.PasswordPepper;

/** Interdit : un autre contexte qu'authentication lit le poivre. */
public class PepperReader {
    byte[] read(PasswordPepper pepper) {
        return pepper.value();
    }
}
