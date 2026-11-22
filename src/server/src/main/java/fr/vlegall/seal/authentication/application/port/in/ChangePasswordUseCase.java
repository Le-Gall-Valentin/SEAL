package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.PasswordChangeOutcome;
import fr.vlegall.seal.common.UserId;

/** ACC-01 : changement du mot de passe depuis les paramètres, avec le mot de passe actuel. */
public interface ChangePasswordUseCase {

    /**
     * @param signOutOtherSessions option « déconnecter mes autres sessions », cochée par défaut (ACC-01)
     * @param currentSession identifiant de la session qui fait la demande, seule gardée si l'option est cochée
     */
    PasswordChangeOutcome change(
            UserId user,
            String currentPassword,
            String newPassword,
            boolean signOutOtherSessions,
            String currentSession);
}
