package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.AiUserState;
import com.rastroos.domain.repository.AiUserStateRepository;
import com.rastroos.web.dto.InsightScreen;

/**
 * Pré-aquecimento: é o que tira a IA do caminho do page load e o que garante
 * que o consumo aconteça <em>uma vez por mudança de dado</em>, não uma vez por
 * visita. Os testes cobrem o debounce, o lote, o isolamento de falhas e o
 * backoff — as quatro coisas que, se quebrarem, viram conta alta.
 */
@ExtendWith(MockitoExtension.class)
class InsightWarmupServiceTest {

    @Mock private AiUserStateRepository states;
    @Mock private UserDataVersionService versions;
    @Mock private ScreenInsightService insights;
    @Mock private VectorIndexService vectors;
    @Mock private AlfredoAiClient ai;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private final UUID alice = UUID.randomUUID();

    private AiProperties props;
    private InsightWarmupService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        service = new InsightWarmupService(states, versions, insights, vectors, ai, props, clock);
    }

    // ── Varredura ────────────────────────────────────────────────────────

    @Test
    void varreduraDesligada_naoTocaEmNada() {
        props.getWarmup().setEnabled(false);

        service.sweep();

        verifyNoInteractions(states, insights, vectors);
    }

    @Test
    void varreduraRespeitaODebounce_soPegaQuemParouDeEscrever() {
        props.getWarmup().setDebounceSeconds(20);
        when(states.findDueForWarmup(any(), any())).thenReturn(List.of());

        service.sweep();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        verify(states).findDueForWarmup(threshold.capture(), any(Limit.class));
        assertThat(threshold.getValue()).isEqualTo(Instant.parse("2026-09-12T11:59:40Z"));
    }

    @Test
    void varreduraLimitaQuantosUsuariosPorVolta() {
        props.getWarmup().setMaxUsersPerSweep(7);
        when(states.findDueForWarmup(any(), any())).thenReturn(List.of());

        service.sweep();

        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(states).findDueForWarmup(any(), limit.capture());
        assertThat(limit.getValue().max()).isEqualTo(7);
    }

    // ── Aquecimento de um usuário ────────────────────────────────────────

    @Test
    void aquecimentoPassaPorTodasAsTelasDosMesesConfigurados_deUmaVezSo() {
        when(ai.isEnabled()).thenReturn(true);
        props.getWarmup().setMonthsBack(1);

        service.warm(alice, 5L);

        // 6 telas com mês × 2 meses + investimentos (sem mês) só uma vez.
        verify(insights, times(13)).insight(eq(alice), any(), any());
        verify(insights).insight(alice, InsightScreen.INVESTMENTS, YearMonth.of(2026, 9));
        verify(insights, never()).insight(alice, InsightScreen.INVESTMENTS, YearMonth.of(2026, 8));
        verify(versions).markWarmed(alice, 5L);
    }

    @Test
    void reservaOUsuarioAntesDeGerar_paraOutraVarreduraNaoRefazerOMesmo() {
        when(ai.isEnabled()).thenReturn(true);

        service.warm(alice, 1L);

        verify(versions).claimForWarmup(alice);
    }

    @Test
    void semIaConfigurada_naoGeraNadaMasEncerraAPendencia() {
        when(ai.isEnabled()).thenReturn(false);

        service.warm(alice, 3L);

        verifyNoInteractions(insights, vectors);
        verify(versions).markWarmed(alice, 3L);
    }

    @Test
    void falhaDoIndiceVetorialNaoDerrubaOsResumos() {
        when(ai.isEnabled()).thenReturn(true);
        when(vectors.reindex(alice)).thenThrow(new AiUnavailableException("sem crédito"));

        service.warm(alice, 2L);

        // Os resumos continuam sendo gerados e o aquecimento fecha normalmente:
        // busca semântica é extra do chat, resumo de tela não depende dela.
        verify(insights, times(13)).insight(eq(alice), any(), any());
        verify(versions).markWarmed(alice, 2L);
        verify(versions, never()).markWarmFailed(any(), anyString(), anyInt());
    }

    @Test
    void falhaDeUmaTelaNaoImpedeAsOutras() {
        when(ai.isEnabled()).thenReturn(true);
        when(insights.insight(eq(alice), eq(InsightScreen.REPORTS), any()))
                .thenThrow(new IllegalStateException("tela com problema"));

        service.warm(alice, 4L);

        verify(insights, times(13)).insight(eq(alice), any(), any());
        verify(versions).markWarmed(alice, 4L);
    }

    @Test
    void falhaGeral_reagendaComBackoffEmVezDeTentarNaVarreduraSeguinte() {
        props.getWarmup().setFailureBackoffSeconds(300);
        when(ai.isEnabled()).thenReturn(true);
        when(insights.insight(any(), any(), any()))
                .thenThrow(new IllegalStateException("falha"));
        // markWarmed é o que explode aqui, simulando falha fora das telas.
        doThrowOnMarkWarmed();

        service.warm(alice, 9L);

        verify(versions).markWarmFailed(eq(alice), anyString(), eq(300));
    }

    private void doThrowOnMarkWarmed() {
        org.mockito.Mockito.doThrow(new IllegalStateException("banco fora"))
                .when(versions).markWarmed(any(), anyLong());
    }
}
