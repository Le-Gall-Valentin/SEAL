package fr.vlegall.seal.ws.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * TRN-14, ADR 0043 : annonce un événement à toutes les connexions ouvertes de ces comptes, par une trame
 * {@code { type, id, payload }} dont le serveur choisit l'{@code id}. Dans une transaction, la trame part après sa
 * validation, et jamais si elle est annulée ; un compte hors ligne ne reçoit rien et relira par l'API.
 */
public interface NotifyUsersUseCase {

    void announce(Set<UserId> users, String type, JsonNode payload);
}
