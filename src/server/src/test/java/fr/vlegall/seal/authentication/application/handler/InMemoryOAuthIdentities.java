package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthException;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link OAuthIdentityRepository} en mémoire, partagé par les tests des cas d'usage d'{@code authentication}, comme
 * {@link InMemoryCredentials} : un compte par couple (fournisseur, sujet), une liaison par fournisseur et par compte.
 */
class InMemoryOAuthIdentities implements OAuthIdentityRepository {

    private final Map<String, UserId> bySubject = new HashMap<>();
    private Consumer<String> linkListener = step -> {};
    private boolean refuseLinks;
    private String forbiddenLinks;

    private static String key(OAuthProvider provider, String subject) {
        return provider + ":" + subject;
    }

    /** Lie le sujet au compte, comme une liaison déjà en base. */
    void put(OAuthProvider provider, String subject, UserId user) {
        bySubject.put(key(provider, subject), user);
    }

    /** Annonce chaque liaison écrite : « liaison:<compte>:<sujet>:<date> », pour vérifier l'ordre des étapes. */
    void onLink(Consumer<String> listener) {
        linkListener = listener;
    }

    /** Les liaisons suivantes échouent comme sur une contrainte d'unicité. */
    void refuseLinks() {
        refuseLinks = true;
    }

    /** Le cas d'usage testé ne doit rien lier : toute liaison fait échouer le test, avec cette raison. */
    void forbidLinks(String reason) {
        forbiddenLinks = reason;
    }

    @Override
    public Optional<UserId> findUser(OAuthProvider provider, String subject) {
        return Optional.ofNullable(bySubject.get(key(provider, subject)));
    }

    @Override
    public boolean isLinked(UserId user, OAuthProvider provider) {
        return bySubject.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(provider + ":")
                        && entry.getValue().equals(user));
    }

    @Override
    public List<OAuthProvider> linkedProviders(UserId user) {
        return Arrays.stream(OAuthProvider.values())
                .filter(provider -> isLinked(user, provider))
                .toList();
    }

    @Override
    public void link(UserId user, ExternalIdentity identity, Instant linkedAt) {
        if (forbiddenLinks != null) {
            throw new UnsupportedOperationException(forbiddenLinks);
        }
        String key = key(identity.provider(), identity.subject());
        if (refuseLinks || bySubject.containsKey(key) || isLinked(user, identity.provider())) {
            throw new OAuthException.AlreadyLinked();
        }
        bySubject.put(key, user);
        linkListener.accept("liaison:" + user.value() + ":" + identity.subject() + ":" + linkedAt);
    }
}
