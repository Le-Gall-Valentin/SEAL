package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.InvitationPolicy;

/** Table {@code user_settings} (ADR 0009). */
@FunctionalInterface
public interface UserSettingsRepository {

    /** ACC-06 : politique d'invitation du compte ; {@link InvitationPolicy#EVERYONE} s'il n'a aucun réglage enregistré. */
    InvitationPolicy invitationPolicy(UserId user);
}
