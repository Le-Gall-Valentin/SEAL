package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.identity.application.port.in.UsernameAvailabilityQuery;
import fr.vlegall.seal.identity.domain.model.ReservedUsernames;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.UsernameAvailability;
import fr.vlegall.seal.identity.domain.model.UsernameSuggestions;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class UsernameAvailabilityHandler implements UsernameAvailabilityQuery {

    /** AUTH-05 : trois suggestions. */
    static final int SUGGESTIONS = 3;

    /** Étape 5 d'AUTH-04, en dernier recours : un nombre borné de tirages, donc de requêtes. */
    static final int RANDOM_ATTEMPTS = 10;

    private final UserRepository users;
    private final RandomDigits random;

    public UsernameAvailabilityHandler(UserRepository users, RandomDigits random) {
        this.users = users;
        this.random = random;
    }

    /**
     * Les suggestions partent de l'identifiant normalisé par les étapes 2 à 4 d'AUTH-04 : sa forme normalisée d'abord,
     * si elle diffère de la saisie, puis les suffixes 2 à 99 vérifiés en une requête, puis 4 chiffres aléatoires.
     */
    @Override
    @Transactional(readOnly = true)
    public UsernameAvailability check(String candidate) {
        boolean available = Username.tryParse(candidate)
                .filter(username -> !ReservedUsernames.contains(username.value()) && !users.exists(username))
                .isPresent();
        String base = UsernameSuggestions.baseFrom(candidate);
        List<Username> candidates = new ArrayList<>();
        if (!base.equals(candidate)) {
            candidates.add(Username.parse(base));
        }
        candidates.addAll(UsernameSuggestions.suffixed(base));
        Set<String> taken = users.takenAmong(candidates);
        List<String> suggestions = new ArrayList<>(SUGGESTIONS);
        for (Username username : candidates) {
            if (suggestions.size() == SUGGESTIONS) {
                break;
            }
            offer(username, taken.contains(username.value()), suggestions);
        }
        for (int attempt = 0; suggestions.size() < SUGGESTIONS && attempt < RANDOM_ATTEMPTS; attempt++) {
            Username username = UsernameSuggestions.randomSuffixed(base, random);
            offer(username, users.exists(username), suggestions);
        }
        return new UsernameAvailability(available, suggestions);
    }

    private static void offer(Username username, boolean taken, List<String> suggestions) {
        if (!taken && !ReservedUsernames.contains(username.value()) && !suggestions.contains(username.value())) {
            suggestions.add(username.value());
        }
    }
}
