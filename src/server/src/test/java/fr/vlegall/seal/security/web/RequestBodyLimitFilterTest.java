package fr.vlegall.seal.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

class RequestBodyLimitFilterTest {

    private static final int LIMIT = 16;

    /** Routes refusées par le filtre, dans l'ordre. */
    private final List<String> rejections = new ArrayList<>();

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**"),
            LIMIT,
            (request, response) -> rejections.add(request.getRequestURI()));

    private static MockHttpServletRequest post(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** Corps transmis par morceaux : aucune longueur annoncée. */
    private static MockHttpServletRequest chunked(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String readBody(ServletRequest request) throws Exception {
        return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("SEC-01 : un corps annoncé au-delà de la limite est refusé sans être lu")
    void sec01DeclaredOversizedBodyIsRefused() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/v1/auth/register", "x".repeat(LIMIT + 1)), new MockHttpServletResponse(), chain);

        assertThat(rejections).containsExactly("/api/v1/auth/register");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("SEC-01 : un corps annoncé à la limite passe tel quel")
    void sec01DeclaredBodyAtTheLimitPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = post("/api/v1/auth/register", "x".repeat(LIMIT));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(rejections).isEmpty();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("SEC-01 : un corps par morceaux qui dépasse la limite est refusé dès le premier octet en trop")
    void sec01ChunkedOversizedBodyIsRefused() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chunked("/api/v1/auth/register", "x".repeat(LIMIT + 1)), new MockHttpServletResponse(), chain);

        assertThat(rejections).containsExactly("/api/v1/auth/register");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("SEC-01 : un corps par morceaux sous la limite est rejoué intact pour la suite de la chaîne")
    void sec01ChunkedSmallBodyIsReplayed() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chunked("/api/v1/auth/register", "{\"é\":1}"), new MockHttpServletResponse(), chain);

        assertThat(rejections).isEmpty();
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded).isNotNull();
        assertThat(readBody(forwarded)).isEqualTo("{\"é\":1}");
        assertThat(forwarded.getContentLengthLong()).isEqualTo("{\"é\":1}".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("SEC-01 : la limite ne vaut que pour les routes désignées")
    void sec01OtherRoutesAreNotLimited() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = post("/api/v1/csrf", "x".repeat(LIMIT * 4));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(rejections).isEmpty();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
