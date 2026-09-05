package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rastroos.config.AlfredoProperties;

/**
 * Cobre o modo demonstração (stub) do {@link AlfredoAiClient} — o padrão
 * quando {@code alfredo.base-url} está vazio: nenhuma chamada externa, resposta
 * determinística. A chamada real e o circuit breaker são exercitados via
 * configuração (integração), não neste unitário.
 */
class AlfredoAiClientTest {

    @Test
    void semBaseUrlRespondeEmModoDemonstracao() {
        AlfredoAiClient client = new AlfredoAiClient(new AlfredoProperties());

        String reply = client.reply("Como economizo este mês?", List.of());

        assertThat(reply).isNotBlank();
        assertThat(reply).contains("Alfredo");
        assertThat(reply).contains("Como economizo este mês?");
    }

    @Test
    void mensagemLongaEhTruncadaNoStub() {
        AlfredoAiClient client = new AlfredoAiClient(new AlfredoProperties());
        String longMessage = "a".repeat(200);

        String reply = client.reply(longMessage, List.of());

        assertThat(reply).contains("…");
        assertThat(reply).doesNotContain("a".repeat(200));
    }

    @Test
    void toleraMensagemNulaOuVazia() {
        AlfredoAiClient client = new AlfredoAiClient(new AlfredoProperties());

        assertThat(client.reply(null, List.of())).isNotBlank();
        assertThat(client.reply("   ", List.of())).isNotBlank();
    }

    @Test
    void summarizeSemBaseUrlNaoChamaNadaExternoEDeixaOResumoLocalNoLugar() {
        AlfredoAiClient client = new AlfredoAiClient(new AlfredoProperties());

        assertThat(client.summarize("Tela: Visão geral\n- Total gasto: R$ 10,00")).isEmpty();
    }
}
