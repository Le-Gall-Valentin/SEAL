package fr.vlegall.seal.ws.application.port.in;

import fr.vlegall.seal.common.UserId;
import tools.jackson.databind.JsonNode;

/**
 * TRN-14, ADR 0043 (point 2) : traitement d'un type de trame reçue sur {@code /ws}. Le contexte qui la traite
 * l'implémente dans son infrastructure ; {@code ws} réunit les implémentations sans dépendre d'elles, ce qui évite tout
 * cycle entre contextes.
 */
public interface InboundFrameHandler {

    /** Nom de la trame traitée, tel qu'il circule (« msg.send »). */
    String type();

    /** La trame naît d'une action de l'utilisateur : elle prolonge la session (AUTH-52, ADR 0043 point 4). */
    boolean userAction();

    /**
     * Traite la charge d'une trame du compte {@code user} et rend celle de l'{@code ack}. Un refus lève une
     * {@link fr.vlegall.seal.common.SealException}, rendue en {@code error} avec son code (section 14.6).
     */
    JsonNode handle(UserId user, JsonNode payload);
}
