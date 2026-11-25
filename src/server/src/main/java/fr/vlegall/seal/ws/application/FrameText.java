package fr.vlegall.seal.ws.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** TRN-14 : texte d'une trame {@code { type, id, payload }}, seule écriture du format. */
public final class FrameText {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FrameText() {}

    public static String of(String type, String id, JsonNode payload) {
        ObjectNode frame = JSON.createObjectNode();
        frame.put("type", type);
        frame.put("id", id);
        frame.set("payload", payload);
        return JSON.writeValueAsString(frame);
    }
}
