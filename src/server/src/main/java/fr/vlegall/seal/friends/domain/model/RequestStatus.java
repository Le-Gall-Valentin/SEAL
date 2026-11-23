package fr.vlegall.seal.friends.domain.model;

/** Section 10.1 : états d'une invitation, colonne {@code friend_requests.status}. */
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED
}
