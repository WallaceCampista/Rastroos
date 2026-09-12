package com.rastroos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestUserAgentTest {

    private static MockHttpServletRequest comUa(String ua) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        if (ua != null) {
            r.addHeader("User-Agent", ua);
        }
        return r;
    }

    @Test
    void devolveOCabecalhoQuandoCabeNaColuna() {
        assertThat(RequestUserAgent.of(comUa("Mozilla/5.0 (Macintosh)"), 400))
                .isEqualTo("Mozilla/5.0 (Macintosh)");
    }

    @Test
    void truncaCabecalhoMaiorQueAColuna() {
        String gigante = "M".repeat(5000);

        String capturado = RequestUserAgent.of(comUa(gigante), 400);

        // Sem o corte, o INSERT estoura e derruba o login — inclusive o de uma
        // tentativa que falhou, que qualquer um alcança sem autenticar.
        assertThat(capturado).hasSize(400);
    }

    @Test
    void semCabecalhoOuVazioDevolveNulo() {
        assertThat(RequestUserAgent.of(comUa(null), 400)).isNull();
        assertThat(RequestUserAgent.of(comUa("   "), 400)).isNull();
    }

    @Test
    void aparaEspacosNasBordas() {
        assertThat(RequestUserAgent.of(comUa("  curl/8.7.1  "), 400)).isEqualTo("curl/8.7.1");
    }
}
