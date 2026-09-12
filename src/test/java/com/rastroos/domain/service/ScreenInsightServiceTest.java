package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.AiInsight;
import com.rastroos.web.dto.InsightDto;
import com.rastroos.web.dto.InsightFacts;
import com.rastroos.web.dto.InsightScreen;

/**
 * Cobre a regra que sustenta o custo do produto: um resumo já gerado é
 * reaproveitado <strong>para sempre</strong> enquanto os números não mudarem.
 * Não há TTL — quem passa um mês sem lançar nada não gera nenhuma chamada, por
 * mais que navegue pelas telas.
 */
@ExtendWith(MockitoExtension.class)
class ScreenInsightServiceTest {

    @Mock private InsightFactsBuilder factsBuilder;
    @Mock private AlfredoAiClient ai;
    @Mock private InsightStore store;
    @Mock private UserDataVersionService versions;
    @Mock private AiModelClient model;

    private AiProperties props;
    private ScreenInsightService service;

    private final UUID userId = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 9);

    private static final InsightFacts FACTS = new InsightFacts(
            InsightScreen.DASHBOARD, "setembro de 2026",
            List.of("Total recebido: R$ 8.500,00", "Falta pagar: R$ 2.200,00"),
            "Resumo local determinístico.");

    private static final String MODEL = "gpt-4o-mini";

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        // O modelo em uso entra na chave do reaproveitamento: trocar de modelo
        // (ou de fornecedor) precisa invalidar o resumo salvo.
        lenient().when(model.chatModel()).thenReturn(MODEL);
        service = new ScreenInsightService(factsBuilder, ai, store, versions, model, props);
    }

    // ── Reaproveitamento (a regra de consumo) ────────────────────────────

    @Test
    void resumoSalvoComOsMesmosNumeros_naoChamaAIaDeNovo() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(store.find(userId, "dashboard", "2026-09"))
                .thenReturn(Optional.of(saved(FACTS.fingerprint(), "Texto guardado.", true)));

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Texto guardado.");
        assertThat(insight.aiGenerated()).isTrue();
        verifyNoInteractions(ai);
        verify(store, never()).save(any(), any(), any(), any(), anyInt(), any(),
                any(), anyBoolean(), anyLong());
    }

    @Test
    void numerosMudaram_geramResumoNovoEGravam() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(store.find(userId, "dashboard", "2026-09"))
                .thenReturn(Optional.of(saved("hash-antigo", "Texto velho.", true)));
        when(ai.summarize(eq(userId), anyString())).thenReturn(Optional.of("Texto novo."));

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Texto novo.");
        verify(store).save(userId, "dashboard", "2026-09", FACTS.fingerprint(),
                props.getInsight().getPromptVersion(), MODEL,
                "Texto novo.", true, 0L);
    }

    @Test
    void versaoDoPromptMudou_invalidaOResumoSalvo() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        AiInsight old = saved(FACTS.fingerprint(), "Texto do prompt antigo.", true);
        old.setPromptVersion(props.getInsight().getPromptVersion() - 1);
        when(store.find(userId, "dashboard", "2026-09")).thenReturn(Optional.of(old));
        when(ai.summarize(eq(userId), anyString())).thenReturn(Optional.of("Texto do prompt novo."));

        assertThat(service.insight(userId, InsightScreen.DASHBOARD, period).text())
                .isEqualTo("Texto do prompt novo.");
    }

    @Test
    void modeloMudou_invalidaOResumoSalvo() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        AiInsight old = saved(FACTS.fingerprint(), "Texto do modelo antigo.", true);
        old.setModel("gpt-4o-mini-antigo");
        when(store.find(userId, "dashboard", "2026-09")).thenReturn(Optional.of(old));
        when(ai.summarize(eq(userId), anyString())).thenReturn(Optional.of("Texto do modelo novo."));

        assertThat(service.insight(userId, InsightScreen.DASHBOARD, period).text())
                .isEqualTo("Texto do modelo novo.");
    }

    // ── Geração ──────────────────────────────────────────────────────────

    @Test
    void semNadaSalvo_geraEMandaOsNumerosNoPrompt() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(store.find(userId, "dashboard", "2026-09")).thenReturn(Optional.empty());
        when(ai.summarize(eq(userId), anyString()))
                .thenReturn(Optional.of("  Seu mês está sob controle.  "));

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Seu mês está sob controle.");
        assertThat(insight.aiGenerated()).isTrue();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).summarize(eq(userId), prompt.capture());
        assertThat(prompt.getValue())
                .contains("Visão geral")
                .contains("setembro de 2026")
                .contains("Total recebido: R$ 8.500,00")
                .contains("Resumo local determinístico.");
    }

    @Test
    void semIaDisponivel_usaOResumoLocalEAindaAssimGuarda() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(store.find(userId, "dashboard", "2026-09")).thenReturn(Optional.empty());
        when(ai.summarize(eq(userId), anyString())).thenReturn(Optional.empty());

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Resumo local determinístico.");
        assertThat(insight.aiGenerated()).isFalse();
        // Guardar o texto local evita insistir numa chamada a cada visita
        // enquanto o provedor estiver fora.
        verify(store).save(userId, "dashboard", "2026-09", FACTS.fingerprint(),
                props.getInsight().getPromptVersion(), MODEL,
                "Resumo local determinístico.", false, 0L);
    }

    @Test
    void quandoAIaExplode_caiNoResumoLocalSemPropagarOErro() {
        when(factsBuilder.build(userId, InsightScreen.DASHBOARD, period)).thenReturn(FACTS);
        when(store.find(userId, "dashboard", "2026-09")).thenReturn(Optional.empty());
        when(ai.summarize(eq(userId), anyString()))
                .thenThrow(new IllegalStateException("provedor fora"));

        InsightDto insight = service.insight(userId, InsightScreen.DASHBOARD, period);

        assertThat(insight.text()).isEqualTo("Resumo local determinístico.");
        assertThat(insight.aiGenerated()).isFalse();
    }

    @Test
    void resumoDeUmUsuarioNaoVazaParaOutro() {
        UUID outro = UUID.randomUUID();
        when(factsBuilder.build(any(UUID.class), any(), any())).thenReturn(FACTS);
        when(store.find(userId, "dashboard", "2026-09"))
                .thenReturn(Optional.of(saved(FACTS.fingerprint(), "Do titular.", true)));
        when(store.find(outro, "dashboard", "2026-09")).thenReturn(Optional.empty());
        when(ai.summarize(eq(outro), anyString())).thenReturn(Optional.of("Do outro."));

        assertThat(service.insight(userId, InsightScreen.DASHBOARD, period).text())
                .isEqualTo("Do titular.");
        assertThat(service.insight(outro, InsightScreen.DASHBOARD, period).text())
                .isEqualTo("Do outro.");
        verify(ai, times(1)).summarize(any(), anyString());
    }

    // ── Casos de borda ───────────────────────────────────────────────────

    @Test
    void telaSemPeriodo_gravaComSentinelaENaoCarregaMesNoDto() {
        InsightFacts semMes = new InsightFacts(InsightScreen.INVESTMENTS, null,
                List.of("Total investido: R$ 10,00"), "Resumo dos investimentos.");
        when(factsBuilder.build(userId, InsightScreen.INVESTMENTS, period)).thenReturn(semMes);
        when(store.find(userId, "investments", AiInsight.NO_PERIOD)).thenReturn(Optional.empty());
        when(ai.summarize(eq(userId), anyString())).thenReturn(Optional.empty());

        InsightDto insight = service.insight(userId, InsightScreen.INVESTMENTS, period);

        assertThat(insight.period()).isNull();
        assertThat(insight.screen()).isEqualTo("investments");
        verify(store).save(eq(userId), eq("investments"), eq(AiInsight.NO_PERIOD),
                anyString(), anyInt(), anyString(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    void resumoMascarado_naoTocaNosDadosNemNaIa() {
        InsightDto masked = service.maskedInsight(InsightScreen.CARDS, period);

        assertThat(masked.text()).contains("ocultar os valores");
        assertThat(masked.text()).doesNotContain("R$");
        assertThat(masked.aiGenerated()).isFalse();
        verifyNoInteractions(factsBuilder, ai, store);
    }

    @Test
    void impressaoDigitalCobreTodasAsLinhas_naoSoOTextoFinal() {
        InsightFacts mesmoTextoOutrosNumeros = new InsightFacts(
                InsightScreen.DASHBOARD, "setembro de 2026",
                List.of("Total recebido: R$ 9.000,00", "Falta pagar: R$ 2.200,00"),
                "Resumo local determinístico.");

        assertThat(mesmoTextoOutrosNumeros.fingerprint()).isNotEqualTo(FACTS.fingerprint());
    }

    private AiInsight saved(String hash, String text, boolean aiGenerated) {
        AiInsight entry = new AiInsight();
        entry.setUserId(userId);
        entry.setScreen("dashboard");
        entry.setPeriod("2026-09");
        entry.setFactsHash(hash);
        entry.setPromptVersion(props.getInsight().getPromptVersion());
        entry.setModel(MODEL);
        entry.setSummaryText(text);
        entry.setAiGenerated(aiGenerated);
        entry.setGeneratedAt(Instant.now());
        return entry;
    }
}
