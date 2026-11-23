package fr.vlegall.seal.keys.domain.port.out;

/** AUTH-55, ADR 0034 : mode sudo de la session qui fait la demande, vérifié dans le handler (SEC-03). */
@FunctionalInterface
public interface SudoModePort {

    /** @throws fr.vlegall.seal.common.SealException {@code REAUTH_REQUIRED} (403) hors du mode sudo */
    void require();
}
