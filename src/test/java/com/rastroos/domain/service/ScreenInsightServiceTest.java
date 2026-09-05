package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.config.AlfredoProperties;
import com.rastroos.web.dto.InsightDto;
import com.rastroos.web.dto.InsightFacts;
import com.rastroos.web.dto.InsightScreen;

@ExtendWith(MockitoExtension.class)
class ScreenInsightServiceTest {

    @Mock private InsightFactsBuilder factsBuilder;
    @Mock private AlfredoAiClient ai;

    private AlfredoProperties props;
    private ScreenInsightService service;

    private final UUID userId = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 9);

    private static final InsightFacts FACTS = new InsightFacts(
            InsightScreen.DASHBOARD, "setembro de 2026",
            List.of("Total recebido: R$ 8.500,00", "Falta pagar: R$ 2.200,00"),
            "Resumo local determinístico.");

    @BeforeEach
    void setUp() {
        props = new AlfredoProperties();
        service = new ScreenInsightService(factsBuilder, ai, props);
    }

    @Test
    void semIaConfigurada_usaOResumoLocalDeterministico() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(ai.summarize(anyString())).thenReturn(Optional.empty());

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Resumo local determinístico.");
        assertThat(insight.aiGenerated()).isFalse();
        assertThat(insight.screen()).isEqualTo("dashboard");
        assertThat(insight.period()).isEqualTo("2026-09");
    }

    @Test
    void comIa_usaOTextoDaIaEMandaOsNumerosNoPrompt() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(ai.summarize(anyString())).thenReturn(Optional.of("  Seu mês está sob controle.  "));

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Seu mês está sob controle.");
        assertThat(insight.aiGenerated()).isTrue();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).summarize(prompt.capture());
        assertThat(prompt.getValue())
                .contains("Visão geral")
                .contains("setembro de 2026")
                .contains("Total recebido: R$ 8.500,00")
                .contains("Resumo local determinístico.");
    }

    @Test
    void quandoAIaExplode_caiNoResumoLocalSemPropagarOErro() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(ai.summarize(anyString())).thenThrow(new IllegalStateException("provedor fora"));

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Resumo local determinístico.");
        assertThat(insight.aiGenerated()).isFalse();
    }

    @Test
    void resumoIgual_reaproveitaOCacheEChamaAIaUmaVezSo() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(ai.summarize(anyString())).thenReturn(Optional.of("Texto da IA."));

        service.insight(userId, InsightScreen.DASHBOARD, period);
        InsightDto second = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(second.text()).isEqualTo("Texto da IA.");
        verify(ai, times(1)).summarize(anyString());
    }

    @Test
    void numerosMudaram_invalidamOCacheEGeramResumoNovo() {
        InsightFacts changed = new InsightFacts(InsightScreen.DASHBOARD, "setembro de 2026",
                FACTS.lines(), "Outro resumo, outros números.");
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period))
                .thenReturn(FACTS, changed);
        when(ai.summarize(anyString())).thenReturn(Optional.of("A"), Optional.of("B"));

        assertThat(service.insight(userId, InsightScreen.DASHBOARD, period).text()).isEqualTo("A");
        assertThat(service.insight(userId, InsightScreen.DASHBOARD, period).text()).isEqualTo("B");

        verify(ai, times(2)).summarize(anyString());
    }

    @Test
    void cacheEhPorUsuario_naoVazaOResumoDeOutraConta() {
        UUID outro = UUID.randomUUID();
        when(factsBuilder.build(any(UUID.class), any(), any())).thenReturn(FACTS);
        when(ai.summarize(anyString())).thenReturn(Optional.of("A"), Optional.of("B"));

        assertThat(service.insight(userId, InsightScreen.DASHBOARD, period).text()).isEqualTo("A");
        assertThat(service.insight(outro, InsightScreen.DASHBOARD, period).text()).isEqualTo("B");
    }

    @Test
    void telaSemPeriodo_naoCarregaMesNoDto() {
        InsightFacts semMes = new InsightFacts(InsightScreen.INVESTMENTS, null,
                List.of("Total investido: R$ 10,00"), "Resumo dos investimentos.");
        when(factsBuilder.build(userId, InsightScreen.INVESTMENTS, period)).thenReturn(semMes);
        when(ai.summarize(anyString())).thenReturn(Optional.empty());

        InsightDto insight = service.insight(userId, InsightScreen.INVESTMENTS, period);

        assertThat(insight.period()).isNull();
        assertThat(insight.screen()).isEqualTo("investments");
    }

    @Test
    void resumoMascarado_naoTocaNosDadosNemNaIa() {
        InsightDto masked = service.maskedInsight(InsightScreen.CARDS, period);

        assertThat(masked.text()).contains("ocultar os valores");
        assertThat(masked.text()).doesNotContain("R$");
        assertThat(masked.aiGenerated()).isFalse();
        verifyNoInteractions(factsBuilder, ai);
    }
}
