package fr.vlegall.archfixture.alpha.domain.model;

import java.io.Serial;

/** Exception témoin : sérialisable par nature (java.lang.Throwable), permise. */
public class AlphaException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AlphaException(String message) {
        super(message);
    }
}
