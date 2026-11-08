package fr.vlegall.seal.identity.domain.model;

import java.util.Set;

/** AUTH-02 : noms réservés, liste complète de l'ADR 0007 (point 5). */
public final class ReservedUsernames {

    private static final Set<String> NAMES = Set.of(
            "admin",
            "root",
            "system",
            "seal",
            "support",
            "security",
            "api",
            "null",
            "me",
            "uqac",
            "administrator",
            "moderator",
            "postmaster",
            "webmaster",
            "hostmaster",
            "abuse",
            "noreply",
            "help",
            "info",
            "contact",
            "anonymous",
            "deleted",
            "test");

    private ReservedUsernames() {}

    public static boolean contains(String name) {
        return NAMES.contains(name);
    }

    public static Set<String> names() {
        return NAMES;
    }
}
