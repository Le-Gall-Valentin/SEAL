package fr.vlegall.seal.keys.domain.model;

/** Structure signée par l'IK dont la signature peut être refusée (A09, ADR 0025, point 10). */
public enum SignedStructure {
    /** KEY-03 : preuve de possession, à la publication de l'identité. */
    PROOF_OF_POSSESSION,

    /** KEY-34 : preuve de possession de la nouvelle IK, à la réinitialisation de l'identité (ADR 0036). */
    RESET_PROOF_OF_POSSESSION,

    /** KEY-05 : liaison d'une sous-clé, à sa publication. */
    SUBKEY_BINDING
}
