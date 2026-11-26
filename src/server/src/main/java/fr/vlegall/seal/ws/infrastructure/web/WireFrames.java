package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TRN-14 : lecture stricte et écriture des trames {@code { "type", "id", "payload" }}. Une trame illisible, à champ
 * inconnu ou en double, ou dont l'{@code id} n'est pas de 1 à 64 caractères {@code [A-Za-z0-9_-]}, est refusée (A10) ;
 * sans charge, elle porte un objet vide (ADR 0043).
 */
@Component
public final class WireFrames {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** Minuscules et points, sans point au début, à la fin ni doublé ; vérifié sans quantificateur imbriqué (ReDoS). */
    private static final Pattern TYPE = Pattern.compile("[a-z][a-z.]{0,63}");

    private static final Set<String> FIELDS = Set.of("type", "id", "payload");

    private final JsonMapper json;

    public WireFrames(JsonMapper json) {
        this.json = json.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    /** La trame lue, ou vide si elle ne suit pas TRN-14. */
    public Optional<WireFrame> read(String text) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JacksonException unreadable) {
            return Optional.empty();
        }
        if (root == null || !root.isObject() || !FIELDS.containsAll(root.propertyNames())) {
            return Optional.empty();
        }
        JsonNode type = root.get("type");
        JsonNode id = root.get("id");
        JsonNode payload = root.get("payload");
        if (type == null
                || !type.isString()
                || !isType(type.asString())
                || id == null
                || !id.isString()
                || !ID.matcher(id.asString()).matches()
                || (payload != null && !payload.isObject())) {
            return Optional.empty();
        }
        return Optional.of(
                new WireFrame(type.asString(), id.asString(), payload == null ? json.createObjectNode() : payload));
    }

    /** Une trame à envoyer. */
    public String frame(String type, String id, JsonNode payload) {
        ObjectNode frame = json.createObjectNode();
        frame.put("type", type);
        frame.put("id", id);
        frame.set("payload", payload);
        return json.writeValueAsString(frame);
    }

    /** {@code error} au même {@code id}, avec un code de la section 14.6. */
    public String error(String id, String code) {
        return frame("error", id, json.createObjectNode().put("code", code));
    }

    /** TRN-13 : {@code error} {@code RATE_LIMITED}, avec le délai en secondes avant la prochaine trame permise. */
    public String rateLimited(String id, Duration retryAfter) {
        long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        return frame(
                "error",
                id,
                json.createObjectNode()
                        .put("code", ErrorCode.RATE_LIMITED.name())
                        .put("retryAfter", seconds));
    }

    private static boolean isType(String type) {
        return TYPE.matcher(type).matches() && !type.endsWith(".") && !type.contains("..");
    }

    /** Un objet vide, charge de {@code pong}. */
    public ObjectNode empty() {
        return json.createObjectNode();
    }
}
