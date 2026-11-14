package fr.vlegall.seal.keys.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Règles d'acceptation d'une sous-clé par le serveur (ADR 0025, points 7 et 15), en plus de son format. */
public final class SubkeyPolicy {

    /** ADR 0025, point 7 : écart toléré entre l'horloge du client et celle du serveur. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    /** ADR 0025, point 15 : sous-clés non expirées au plus par compte. */
    public static final int MAX_LIVE = 3;

    private SubkeyPolicy() {}

    /**
     * Champs hors des bornes, dans l'ordre du format : {@code createdAt} à plus de {@link #CLOCK_SKEW} de {@code now},
     * {@code expiresAt} qui ne vaut pas {@code createdAt} plus 45 jours (KEY-21). Les écarts se comparent par
     * {@link Duration}, qui couvre tout l'intervalle des {@link Instant} : aucune date extrême ne fait déborder le calcul.
     */
    public static List<String> outOfBounds(Instant createdAt, Instant expiresAt, Instant now) {
        List<String> fields = new ArrayList<>();
        if (Duration.between(now, createdAt).abs().compareTo(CLOCK_SKEW) > 0) {
            fields.add("createdAt");
        }
        if (!Duration.between(createdAt, expiresAt).equals(SubkeyBinding.VALIDITY)) {
            fields.add("expiresAt");
        }
        return fields;
    }
}
