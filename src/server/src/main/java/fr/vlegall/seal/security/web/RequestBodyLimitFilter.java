package fr.vlegall.seal.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEC-01, A06 : borne la taille du corps des requêtes désignées (ADR 0019, point 5), avant que Jackson ne le lise. Un
 * corps annoncé trop grand est refusé sans être lu ; un corps sans longueur annoncée (transfert par morceaux) est lu
 * jusqu'à la limite, puis rejoué. Placé après l'écriture des en-têtes de sécurité, pour que le refus les porte.
 *
 * <p>Créé par {@code SecurityConfig}, jamais déclaré comme composant : Spring Boot l'enregistrerait une seconde fois
 * comme filtre de servlet.
 */
public final class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** Réponse au corps trop grand : 413 {@code REQUEST_REJECTED} en Problem Details, en production. */
    @FunctionalInterface
    public interface Rejection {

        void reject(HttpServletRequest request, HttpServletResponse response) throws IOException;
    }

    private final RequestMatcher matcher;
    private final int limit;
    private final Rejection rejection;

    /** @param limit taille maximale du corps, en octets, incluse */
    public RequestBodyLimitFilter(RequestMatcher matcher, int limit, Rejection rejection) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.limit = limit;
        this.rejection = Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            rejection.reject(request, response);
            return;
        }
        if (declared >= 0) {
            // Le conteneur ne livre jamais plus que la longueur annoncée.
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(limit + 1);
        if (body.length > limit) {
            rejection.reject(request, response);
            return;
        }
        chain.doFilter(new ReplayedBodyRequest(request, body), response);
    }

    /** Requête dont le corps, déjà lu et borné, est rejoué depuis la mémoire. */
    private static final class ReplayedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        ReplayedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bytes = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bytes.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return bytes.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return bytes.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("lecture asynchrone non prise en charge");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
