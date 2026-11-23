package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityReset;
import fr.vlegall.seal.keys.domain.model.Vault;

/** KEY-34, ADR 0036 (point 1) : réinitialisation de l'identité, en mode sudo et en une seule transaction. */
public interface ResetIdentityUseCase {

    /**
     * Vérifie le mode sudo, consomme le défi, vérifie la preuve de possession de {@code ikPublic}, écrit le coffre s'il
     * suit la version enregistrée, puis fait remplacer le certificat. Le défi est consommé même si la preuve est
     * fausse (KEY-03).
     *
     * @throws fr.vlegall.seal.common.SealException {@code REAUTH_REQUIRED} hors du mode sudo ; {@code FORBIDDEN} pour
     *     un compte qui n'est plus actif ou un remplacement refusé, ce qui annule la transaction, coffre compris
     */
    IdentityReset reset(UserId user, byte[] ikPublic, byte[] challenge, byte[] signature, Vault vault);
}
