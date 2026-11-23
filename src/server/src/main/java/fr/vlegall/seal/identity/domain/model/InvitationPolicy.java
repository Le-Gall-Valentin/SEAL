package fr.vlegall.seal.identity.domain.model;

/** ACC-06, FRD-03 : à qui le compte ouvre ses invitations, colonne {@code user_settings.invitation_policy}. */
public enum InvitationPolicy {
    /** Valeur par défaut, aussi pour un compte sans ligne dans {@code user_settings}. */
    EVERYONE,
    NOBODY
}
