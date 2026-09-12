package com.rastroos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;

/**
 * Proteção contra rajada nas rotas que gastam IA. O teto diário limita o
 * total do dia; sem este limite, ele seria consumido inteiro num minuto.
 */
@ExtendWith(MockitoExtension.class)
class AiRateLimitFilterTest {

    @Mock private CurrentUser currentUser;
    @Mock private FilterChain chain;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private AiRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AiRateLimitFilter(currentUser);
        ReflectionTestUtils.setField(filter, "requestsPerWindow", 3);
        ReflectionTestUtils.setField(filter, "windowMinutes", 1);
    }

    @Test
    void dentroDoLimite_aRequisicaoPassa() throws Exception {
        when(currentUser.id()).thenReturn(Optional.of(alice));

        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/api/v1/chats"), new MockHttpServletResponse(), chain);
        }

        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acimaDoLimite_recebe429ComRetryAfterENaoChegaAoControlador() throws Exception {
        when(currentUser.id()).thenReturn(Optional.of(alice));
        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/api/v1/chats"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/chats"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("AI_RATE_LIMITED");
        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void limiteEhPorConta_naoPorIp() throws Exception {
        when(currentUser.id()).thenReturn(Optional.of(alice), Optional.of(alice),
                Optional.of(alice), Optional.of(bob));
        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/api/v1/chats"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse bobResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/chats"), bobResponse, chain);

        assertThat(bobResponse.getStatus()).isEqualTo(200);
        assertThat(filter.trackedBuckets()).hasSize(2);
    }

    @Test
    void respostaDeFormularioEhTexto_naoJson() throws Exception {
        when(currentUser.id()).thenReturn(Optional.of(alice));
        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/app/manager/new"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/app/manager/new"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).startsWith("text/plain");
    }

    @Test
    void rotasQueNaoGastamIaNaoSaoLimitadas() throws Exception {
        MockFilterChain passthrough = new MockFilterChain();

        filter.doFilter(post("/app/expenses/new"), new MockHttpServletResponse(), passthrough);

        // shouldNotFilter verdadeiro: nem consulta quem é o usuário.
        verify(currentUser, never()).id();
    }

    @Test
    void getNaoEhLimitado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chats");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(currentUser, never()).id();
    }

    @Test
    void semUsuarioNoContexto_aRequisicaoSegue_quemBarraAnonimoEhOSpringSecurity()
            throws Exception {
        when(currentUser.id()).thenReturn(Optional.empty());

        filter.doFilter(post("/api/v1/chats"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertThat(filter.trackedBuckets()).isEmpty();
    }

    @Test
    void aberturaDeConversaAPartirDoResumoTambemEhLimitada() throws Exception {
        when(currentUser.id()).thenReturn(Optional.of(alice));
        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/api/v1/insights/dashboard/chat"),
                    new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/insights/dashboard/chat"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void leituraDeDocumentoPorVisaoTambemEhLimitada() throws Exception {
        when(currentUser.id()).thenReturn(Optional.of(alice));
        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/app/expenses/extract"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/app/expenses/extract"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }
}
