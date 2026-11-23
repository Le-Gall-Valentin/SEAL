package fr.vlegall.seal.friends.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import java.util.Map;
import java.util.UUID;

/** Section 10.1 : 409 {@code INCOMING_REQUEST_EXISTS}, avec l'invitation reçue dans la propriété {@code requestId}. */
final class IncomingRequestExists extends SealException {

    private static final long serialVersionUID = 1L;

    private final String requestId;

    IncomingRequestExists(UUID requestId) {
        super(ErrorCode.INCOMING_REQUEST_EXISTS);
        this.requestId = requestId.toString();
    }

    @Override
    public Map<String, String> properties() {
        return Map.of("requestId", requestId);
    }
}
