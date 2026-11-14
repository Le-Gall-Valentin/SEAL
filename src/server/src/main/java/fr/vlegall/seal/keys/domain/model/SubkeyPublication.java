package fr.vlegall.seal.keys.domain.model;

import java.util.List;

/** Issue d'une publication de sous-clé (KEY-05, ADR 0025 points 5 et 7). */
public sealed interface SubkeyPublication {

    /** Sous-clé enregistrée. */
    record Published() implements SubkeyPublication {}

    /** Aucun certificat valide, ou {@code certSerial} qui n'est pas le certificat courant : 404 {@code KEY_NOT_FOUND}. */
    record NoCurrentCertificate() implements SubkeyPublication {}

    /** Dates hors des bornes de l'ADR 0025, point 7 : 400 {@code VALIDATION_FAILED} sur ces champs. */
    record OutOfBounds(List<String> fields) implements SubkeyPublication {
        public OutOfBounds {
            fields = List.copyOf(fields);
        }
    }

    /** Liaison qui ne vérifie pas avec la clé d'identité certifiée : 422 {@code INVALID_SIGNATURE}. */
    record InvalidSignature() implements SubkeyPublication {}

    /** KEY-05 : {@code ekId} déjà utilisé, 400 {@code VALIDATION_FAILED} sur {@code ekId}. */
    record DuplicateEkId() implements SubkeyPublication {}
}
