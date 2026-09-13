package com.rastroos.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

class RastroosErrorViewResolverTest {

    private final RastroosErrorViewResolver resolver = new RastroosErrorViewResolver();

    @ParameterizedTest(name = "{0} em {1} → {2}")
    @CsvSource({
            "404, /app/qualquer,          error.404",
            "403, /app/users,             error.403",
            "403, /app/manager,           error.403ai",
            "403, /app/manager/abc/send,  error.403ai",
            "403, /api/v1/chats/1/stream, error.403ai",
            "403, /api/v1/insights/x,     error.403ai",
            "403, /app/managerial,        error.403",
            "418, /app/cha,               error.4xx",
            "502, /app/x,                 error.5xx",
            "500, /app/x,                 error.500"
    })
    void escolheATextoCertoPorStatusERota(int status, String path, String expected) {
        assertThat(RastroosErrorViewResolver.keyFor(status, path.trim())).isEqualTo(expected);
    }

    /**
     * O modelo é montado do zero: mesmo que o mapa de entrada traga mensagem,
     * trace e exceção (perfil dev), a página não os recebe.
     */
    @Test
    void modeloNaoCarregaMensagemTraceNemExcecao() {
        Map<String, Object> entrada = Map.of(
                "path", "/app/x",
                "message", "SEGREDO",
                "trace", "at com.rastroos...",
                "exception", "java.lang.IllegalStateException");

        ModelAndView mav = resolver.resolveErrorView(new MockHttpServletRequest(),
                HttpStatus.INTERNAL_SERVER_ERROR, entrada);

        assertThat(mav.getViewName()).isEqualTo("error/page");
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(mav.getModel()).doesNotContainKeys("message", "trace", "exception");
        assertThat(mav.getModel()).containsEntry("path", "/app/x").containsEntry("status", 500);
    }

    @Test
    void erroDeServidorOfereceAbrirChamado() {
        ModelAndView mav = resolver.resolveErrorView(new MockHttpServletRequest(),
                HttpStatus.INTERNAL_SERVER_ERROR, Map.of("path", "/app/x"));

        assertThat(mav.getModel())
                .containsEntry("actionHref", RastroosErrorViewResolver.HOME)
                .containsEntry("secondaryHref", RastroosErrorViewResolver.SUPPORT);
    }

    @Test
    void sessaoExpiradaLevaAoLogin() {
        ModelAndView mav = resolver.resolveErrorView(new MockHttpServletRequest(),
                HttpStatus.UNAUTHORIZED, Map.of("path", "/app/x"));

        assertThat(mav.getModel())
                .containsEntry("actionKey", "error.action.login")
                .containsEntry("actionHref", RastroosErrorViewResolver.LOGIN)
                .doesNotContainKey("secondaryHref");
    }

    @Test
    void semPathNoModeloUsaOEnderecoDaRequisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/de-onde-veio");

        ModelAndView mav = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

        assertThat(mav.getModel()).containsEntry("path", "/app/de-onde-veio");
    }
}
