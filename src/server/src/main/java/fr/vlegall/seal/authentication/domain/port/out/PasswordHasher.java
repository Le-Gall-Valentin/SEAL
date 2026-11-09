package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.Password;

/** Hachage des mots de passe (AUTH-13). */
public interface PasswordHasher {

    /** Haché préfixé par l'identifiant de son algorithme, pour pouvoir durcir les paramètres plus tard. */
    String hash(Password password);

    boolean matches(Password password, String encoded);

    /** Vrai si {@code encoded} est un haché que ce hacheur produit aujourd'hui : jamais un texte en clair. */
    boolean recognises(String encoded);

    /**
     * AUTH-21 : vérifie le mot de passe contre un haché leurre, au coût d'une vraie vérification, sans rien en rendre.
     * Une connexion sans compte ou sans haché dure ainsi autant qu'un mauvais mot de passe.
     */
    void verifyDecoy(Password password);
}
