package com.rastroos.domain.service;

import org.springframework.web.client.RestClient;

/**
 * Um motor de IA pronto para uso: o dialeto do fornecedor, a credencial dele e
 * os clientes HTTP já configurados.
 *
 * <p>Existe um por fornecedor configurado, montado no boot. Alternar de motor
 * é trocar de instância — nada é reconstruído em tempo de requisição.
 *
 * @param textClient   {@code null} quando o fornecedor não tem credencial:
 *                     é o que marca o motor como indisponível
 */
public record AiEngine(
        AiProvider provider,
        String apiKey,
        String baseUrl,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        RestClient textClient,
        RestClient visionClient,
        RestClient invoiceClient
) {
    public String id() {
        return provider.id();
    }

    /** Sem credencial não há chamada: o app responde em modo demonstração. */
    public boolean enabled() {
        return textClient != null;
    }
}
