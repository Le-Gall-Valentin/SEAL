package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.DisplayNameRule;
import fr.vlegall.seal.common.Unicode;
import java.util.Objects;

/**
 * GRP-01, ADR 0039 (point 6) : nom d'un groupe, normalisé en NFC, sous la règle du nom affiché (ni caractère de
 * contrôle, de direction ou invisible selon l'ADR 0040, un caractère visible), sur 1 à 64 points de code.
 */
public record GroupName(String value) {

    public static final int MAX_LENGTH = 64;

    /** SEC-01 : borne du corps avant normalisation, en unités UTF-16 (le double de {@link #MAX_LENGTH}). */
    public static final int RAW_MAX_LENGTH = 2 * MAX_LENGTH;

    public GroupName {
        Objects.requireNonNull(value, "value");
    }

    public static GroupName parse(String raw) {
        String normalized = Unicode.nfc(raw);
        if (!DisplayNameRule.isValid(normalized, MAX_LENGTH)) {
            throw new ConversationException.InvalidName();
        }
        return new GroupName(normalized);
    }
}
