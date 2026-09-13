package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.rastroos.domain.entity.AppSetting;
import com.rastroos.domain.repository.AppSettingRepository;

/**
 * Qual motor de IA vale, e quem pode trocar. Errar aqui manda o dossiê
 * financeiro para o fornecedor errado — ou desliga a IA sem avisar.
 */
@ExtendWith(MockitoExtension.class)
class AiProviderSettingTest {

    @Mock private AppSettingRepository settings;

    private AiProperties props;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setProvider("openai");
        props.getKeys().setOpenai("chave-openai");
        props.getKeys().setGemini("chave-gemini");
    }

    private AiProviderSetting service() {
        return new AiProviderSetting(settings, props,
                List.of(new OpenAiProvider(), new GeminiProvider()));
    }

    @Test
    void semEscolhaGravadaValeODoAmbiente() {
        when(settings.findById(AiProviderSetting.KEY)).thenReturn(Optional.empty());

        assertThat(service().current()).isEqualTo("openai");
    }

    @Test
    void escolhaGravadaVenceODoAmbiente() {
        when(settings.findById(AiProviderSetting.KEY))
                .thenReturn(Optional.of(new AppSetting(AiProviderSetting.KEY, "gemini", admin)));

        assertThat(service().current()).isEqualTo("gemini");
    }

    /** Fornecedor que saiu do código não pode deixar a IA apontando para o nada. */
    @Test
    void escolhaGravadaDesconhecidaCaiNoAmbiente() {
        when(settings.findById(AiProviderSetting.KEY))
                .thenReturn(Optional.of(new AppSetting(AiProviderSetting.KEY, "motor-extinto", admin)));

        assertThat(service().current()).isEqualTo("openai");
    }

    @Test
    void trocaPersisteEPassaAValerNaHora() {
        when(settings.findById(AiProviderSetting.KEY)).thenReturn(Optional.empty());
        AiProviderSetting service = service();

        service.set("gemini", admin);

        ArgumentCaptor<AppSetting> saved = ArgumentCaptor.forClass(AppSetting.class);
        verify(settings).save(saved.capture());
        assertThat(saved.getValue().getKey()).isEqualTo(AiProviderSetting.KEY);
        assertThat(saved.getValue().getValue()).isEqualTo("gemini");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo(admin);
        // Sem reler o banco: o cache foi atualizado junto.
        assertThat(service.current()).isEqualTo("gemini");
    }

    @Test
    void trocaParaFornecedorDesconhecidoERecusada() {
        assertThatThrownBy(() -> service().set("hal9000", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.providerUnknown");
        verify(settings, never()).save(any());
    }

    /** Trocar para um motor sem chave desligaria a IA inteira em silêncio. */
    @Test
    void trocaParaFornecedorSemChaveERecusada() {
        props.getKeys().setGemini("");

        assertThatThrownBy(() -> service().set("gemini", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.providerNotConfigured");
        verify(settings, never()).save(any());
    }

    @Test
    void ambienteTravadoIgnoraOBancoERecusaTroca() {
        props.setProviderLocked(true);
        props.setProvider("gemini");
        AiProviderSetting service = service();

        assertThat(service.locked()).isTrue();
        assertThat(service.current()).isEqualTo("gemini");
        assertThatThrownBy(() -> service.set("openai", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.providerLocked");
        verify(settings, never()).findById(any());
        verify(settings, never()).save(any());
    }

    @Test
    void configuredRefleteAPresencaDaChaveDaquelaFornecedor() {
        props.getKeys().setOpenai("");
        AiProviderSetting service = service();

        assertThat(service.configured("openai")).isFalse();
        assertThat(service.configured("gemini")).isTrue();
        assertThat(service.known()).containsExactly("openai", "gemini");
    }

    /** Sem chave por fornecedor, a chave geral ainda serve os dois. */
    @Test
    void chaveGeralServeDeFallback() {
        props.getKeys().setOpenai("");
        props.getKeys().setGemini("");
        props.setApiKey("chave-unica");

        assertThat(service().configured("openai")).isTrue();
        assertThat(service().configured("gemini")).isTrue();
    }
}
