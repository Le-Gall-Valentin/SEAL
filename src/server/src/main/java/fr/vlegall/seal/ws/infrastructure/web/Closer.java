package fr.vlegall.seal.ws.infrastructure.web;

/** TRN-12, ADR 0044 : ferme une connexion hors du fil appelant, ou sur lui si le report est impossible. */
@FunctionalInterface
interface Closer {

    void close(Runnable close);
}
