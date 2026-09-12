package com.rastroos.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PeriodResolverTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"));

    private MockHttpServletRequest request;
    private PeriodResolver resolver;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        resolver = new PeriodResolver(FIXED, request);
    }

    @Test
    void semYmESemSessaoUsaMesCorrente() {
        assertThat(resolver.resolve(null)).isEqualTo(YearMonth.of(2026, 5));
    }

    @Test
    void ymValidoVenceEFicaMemorizado() {
        assertThat(resolver.resolve("2026-07")).isEqualTo(YearMonth.of(2026, 7));
        assertThat(request.getSession().getAttribute(PeriodResolver.SESSION_KEY)).isEqualTo("2026-07");
    }

    @Test
    void semYmDepoisDeEscolherMantemOMesEscolhido() {
        resolver.resolve("2026-07");

        // É o bug do relatório: trocar de tela não carrega ym na URL, e antes
        // disso todo controller caía de volta no mês corrente.
        assertThat(resolver.resolve(null)).isEqualTo(YearMonth.of(2026, 7));
        assertThat(resolver.resolve("")).isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    void ymInvalidoNaoApagaAEscolhaAnterior() {
        resolver.resolve("2026-07");

        assertThat(resolver.resolve("nao-e-um-mes")).isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    void ymInvalidoSemEscolhaAnteriorCaiNoMesCorrente() {
        assertThat(resolver.resolve("2026-13")).isEqualTo(YearMonth.of(2026, 5));
    }

    @Test
    void forgetVoltaAoMesCorrente() {
        resolver.resolve("2026-07");

        resolver.forget();

        assertThat(resolver.resolve(null)).isEqualTo(YearMonth.of(2026, 5));
    }

    @Test
    void sessaoNovaComecaNoMesCorrente() {
        resolver.resolve("2026-07");

        // Sessão nova = novo login: o padrão volta a ser o mês corrente.
        MockHttpServletRequest outra = new MockHttpServletRequest();
        assertThat(new PeriodResolver(FIXED, outra).resolve(null)).isEqualTo(YearMonth.of(2026, 5));
    }
}
