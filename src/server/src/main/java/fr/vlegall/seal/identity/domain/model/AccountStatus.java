package fr.vlegall.seal.identity.domain.model;

/** Statut d'un compte, colonne {@code users.status} (section 13 du CDC). */
public enum AccountStatus {
    /** AUTH-14 : courriel non vérifié ; session restreinte seulement (AUTH-16). */
    PENDING_EMAIL,
    ACTIVE,
    /** ACC-07 : compte supprimé ; l'identifiant reste pris (AUTH-03). */
    DELETED
}
