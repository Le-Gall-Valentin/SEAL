package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.OpenedDirect;

/** CNV-01, FRD-21 : trouver ou créer la directe de deux amis. */
public interface OpenDirectConversationUseCase {

    OpenedDirect open(UserId caller, UserId other);
}
