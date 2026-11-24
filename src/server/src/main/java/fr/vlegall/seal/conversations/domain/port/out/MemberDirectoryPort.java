package fr.vlegall.seal.conversations.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.MemberProfile;
import java.util.Collection;
import java.util.List;

/** Profils des comptes actifs (contexte {@code identity}) ; un compte inactif ou inconnu est absent de la réponse. */
@FunctionalInterface
public interface MemberDirectoryPort {

    List<MemberProfile> activeProfiles(Collection<UserId> users);
}
