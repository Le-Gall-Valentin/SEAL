package fr.vlegall.seal.ws.infrastructure.web;

/** TRN-12, ADR 0044 : ferme une connexion hors du fil appelant, ou vite sur lui si le report est impossible. */
@FunctionalInterface
interface Closer {

    /** {@code patient} hors du fil appelant ; sinon {@code urgent}, qui ne bloque jamais longtemps. */
    void close(Runnable patient, Runnable urgent);
}
