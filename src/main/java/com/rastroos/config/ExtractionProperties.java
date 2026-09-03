package com.rastroos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da extração de gasto por documento/foto. Por padrão fica
 * <strong>desligada</strong> ({@code base-url} vazio) e o app roda em modo
 * demonstração (stub, sem chamada externa). Preenchendo {@code base-url}
 * (e {@code api-key}) habilita-se a IA de visão real (multimodal), nos moldes
 * do Alfredo — a implementação do cliente remoto é o próximo passo.
 */
@ConfigurationProperties(prefix = "extraction")
public class ExtractionProperties {

    /** Endpoint do modelo de visão (vazio = modo stub). */
    private String baseUrl = "";

    /** Chave de API enviada como Bearer (nunca logar). */
    private String apiKey = "";

    /** Modelo multimodal solicitado ao provedor. */
    private String model = "";

    /** Tamanho máximo aceito por arquivo (bytes). Defesa em profundidade além do limite do multipart. */
    private long maxFileSizeBytes = 8L * 1024 * 1024;

    /** Timeout de conexão (ms). */
    private int connectTimeoutMs = 3000;

    /** Timeout de leitura (ms). */
    private int readTimeoutMs = 20000;

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
