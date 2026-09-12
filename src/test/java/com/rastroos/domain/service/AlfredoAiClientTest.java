package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.entity.enums.ChatMessageRole;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Cobre o {@link AlfredoAiClient}: o modo demonstração (sem IA configurada) e,
 * com a IA ligada, o que de fato vai no prompt — em especial o contrato
 * anti-invenção e o dossiê de dados reais, que é o que separa uma resposta
 * conferível de um chute bem escrito.
 */
@ExtendWith(MockitoExtension.class)
class AlfredoAiClientTest {

    @Mock private AiModelClient model;
    @Mock private AiBudgetGuard budget;

    private AiProperties props;
    private AlfredoAiClient client;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        // Breaker real, com a configuração padrão: as falhas isoladas destes
        // testes não chegam perto do limiar, então ele nunca abre e o que se
        // observa é o comportamento do cliente, não o do Resilience4j.
        client = new AlfredoAiClient(model, budget,
                new AiCircuitBreakers(CircuitBreakerRegistry.ofDefaults()), props);
    }

    // ── Modo demonstração ────────────────────────────────────────────────

    @Test
    void semIaConfigurada_respondeEmModoDemonstracaoSemChamarNada() {
        when(model.isEnabled()).thenReturn(false);

        String reply = client.reply(userId, "Como economizo este mês?", List.of(), null);

        assertThat(reply).isNotBlank();
        assertThat(reply).contains("Alfredo");
        assertThat(reply).contains("Como economizo este mês?");
        verify(model, never()).chat(any(), any(), any(), anyInt(), anyDouble(), any());
        verifyNoInteractions(budget);
    }

    @Test
    void mensagemLongaEhTruncadaNoStub() {
        when(model.isEnabled()).thenReturn(false);

        String reply = client.reply(userId, "a".repeat(200), List.of(), null);

        assertThat(reply).contains("…");
        assertThat(reply).doesNotContain("a".repeat(200));
    }

    @Test
    void toleraMensagemNulaOuVazia() {
        when(model.isEnabled()).thenReturn(false);

        assertThat(client.reply(userId, null, List.of(), null)).isNotBlank();
        assertThat(client.reply(userId, "   ", List.of(), null)).isNotBlank();
    }

    @Test
    void summarizeSemIaNaoChamaNadaExternoEDeixaOResumoLocalNoLugar() {
        when(model.isEnabled()).thenReturn(false);

        assertThat(client.summarize(userId, "Tela: Visão geral\n- Total gasto: R$ 10,00")).isEmpty();
    }

    // ── Com IA ligada ────────────────────────────────────────────────────

    @Test
    void comIa_mandaContratoAntiInvencaoEODossieAntesDaPergunta() {
        enableModel("Você gastou R$ 1.200,00 em setembro.");

        client.reply(userId, "Quanto gastei?", List.of(), "DADOS REAIS DE SETEMBRO\n- Gasto: R$ 1.200,00");

        List<Map<String, Object>> sent = captureMessages();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).get("role")).isEqualTo("system");
        assertThat((String) sent.get(0).get("content"))
                .contains("REGRAS INVIOLÁVEIS SOBRE NÚMEROS")
                .contains("aparecer literalmente no bloco DADOS REAIS")
                .contains("proibido estimar")
                .contains("diga que não sabe");
        assertThat(sent.get(1).get("role")).isEqualTo("system");
        assertThat((String) sent.get(1).get("content")).contains("R$ 1.200,00");
        assertThat(sent.get(2)).containsEntry("role", "user").containsEntry("content", "Quanto gastei?");
    }

    @Test
    void semDossie_naoMandaMensagemDeContextoVazia() {
        enableModel("resposta");

        client.reply(userId, "oi", List.of(), "   ");

        List<Map<String, Object>> sent = captureMessages();
        assertThat(sent).hasSize(2);
    }

    @Test
    void historicoRespeitaAJanelaConfigurada() {
        enableModel("resposta");
        props.getChat().setHistoryWindow(2);

        client.reply(userId, "e agora?", List.of(
                msg(ChatMessageRole.USER, "primeira"),
                msg(ChatMessageRole.ASSISTANT, "segunda"),
                msg(ChatMessageRole.USER, "terceira"),
                msg(ChatMessageRole.ASSISTANT, "quarta")), null);

        List<Map<String, Object>> sent = captureMessages();
        // 1 sistema + 2 do histórico + a pergunta
        assertThat(sent).hasSize(4);
        assertThat(sent.get(1)).containsEntry("role", "user").containsEntry("content", "terceira");
        assertThat(sent.get(2)).containsEntry("role", "assistant").containsEntry("content", "quarta");
    }

    @Test
    void tetoDiarioAtingido_devolveRecadoEspecificoSemChamarOProvedor() {
        when(model.isEnabled()).thenReturn(true);
        doThrow(new AiBudgetExceededException("estourou"))
                .when(budget).check(userId, AiFeature.CHAT);

        String reply = client.reply(userId, "Quanto gastei?", List.of(), "DADOS");

        assertThat(reply).contains("limite");
        assertThat(reply).contains("Amanhã");
        verify(model, never()).chat(any(), any(), any(), anyInt(), anyDouble(), any());
    }

    @Test
    void provedorForaDoAr_caiNaRespostaDeContingenciaSemPropagar() {
        when(model.isEnabled()).thenReturn(true);
        when(model.chat(eq(AiFeature.CHAT), eq(userId), any(), anyInt(), anyDouble(), any()))
                .thenThrow(new AiUnavailableException("timeout"));

        String reply = client.reply(userId, "Quanto gastei?", List.of(), "DADOS");

        assertThat(reply).contains("não consegui falar com o motor de IA");
    }

    @Test
    void summarizeComIa_usaOPromptDeResumoEOsLimitesDeToken() {
        when(model.isEnabled()).thenReturn(true);
        props.getInsight().setMaxTokens(123);
        when(model.chat(eq(AiFeature.INSIGHT), eq(userId), any(), eq(123), anyDouble(), any()))
                .thenReturn(new AiCompletion("Resumo curto.", AiTokenUsage.ZERO));

        assertThat(client.summarize(userId, "Tela: Visão geral")).contains("Resumo curto.");

        List<Map<String, Object>> sent = captureMessages(AiFeature.INSIGHT);
        assertThat((String) sent.get(0).get("content")).contains("no máximo 55 palavras");
    }

    @Test
    void summarizeComProvedorForaDoAr_devolveVazioParaCairNoResumoLocal() {
        when(model.isEnabled()).thenReturn(true);
        when(model.chat(eq(AiFeature.INSIGHT), eq(userId), any(), anyInt(), anyDouble(), any()))
                .thenThrow(new AiUnavailableException("503"));

        assertThat(client.summarize(userId, "Tela: Visão geral")).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void enableModel(String answer) {
        when(model.isEnabled()).thenReturn(true);
        when(model.chat(eq(AiFeature.CHAT), eq(userId), any(), anyInt(), anyDouble(), any()))
                .thenReturn(new AiCompletion(answer, AiTokenUsage.ZERO));
    }

    private List<Map<String, Object>> captureMessages() {
        return captureMessages(AiFeature.CHAT);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> captureMessages(AiFeature feature) {
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(model).chat(eq(feature), eq(userId), captor.capture(), anyInt(), anyDouble(), any());
        return captor.getValue();
    }

    private static ChatMessage msg(ChatMessageRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
