package fr.vlegall.seal.identity.domain.model;

import java.time.Duration;

/** AUTH-14 : durée de vie et nombre d'essais d'un code de vérification (section 15.2 du CDC). */
public final class CodePolicy {

    /** Un code vit 10 minutes. */
    public static final Duration VALIDITY = Duration.ofMinutes(10);

    /** 5 essais par code, comptés dans {@code email_codes.attempts} (SEC-10). */
    public static final int MAX_ATTEMPTS = 5;

    private CodePolicy() {}
}
