package fr.vlegall.seal.authentication.domain.port.out;

/** Liste de blocage d'AUTH-11 (PROTOCOL.md, section 3.1). */
@FunctionalInterface
public interface Blocklist {

    /** Vrai si la liste contient ce mot de passe, déjà replié par {@code PasswordPolicy.fold}. */
    boolean contains(String folded);
}
