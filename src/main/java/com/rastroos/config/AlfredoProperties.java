package com.rastroos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da integração com a IA "Alfredo". Por padrão fica
 * <strong>desligada</strong> ({@code base-url} vazio) e o app responde em
 * modo demonstração (stub), sem chamada externa. Preenchendo {@code base-url}
 * (e {@code api-key}) a chamada real é habilitada, protegida por timeout e
 * circuit breaker.
 */
@ConfigurationProperties(prefix = "alfredo")
public class AlfredoProperties {

    /** Endpoint completo de chat completions (vazio = modo stub). */
    private String baseUrl = "";

    /** Chave de API enviada como Bearer (nunca logar). */
    private String apiKey = "";

    /** Modelo solicitado ao provedor. */
    private String model = "gpt-4o-mini";

    /** Instrução de sistema que define a persona do Alfredo. */
    private String systemPrompt =
            "Você é o Alfredo, o gerente financeiro pessoal do Rastro$. "
            + "Responda em português do Brasil, de forma objetiva e acolhedora, "
            + "com foco em organização financeira, sem prometer rendimentos.";

    /** Timeout de conexão (ms). */
    private int connectTimeoutMs = 3000;

    /** Timeout de leitura (ms). */
    private int readTimeoutMs = 15000;

    /** Quantas mensagens recentes do histórico enviar como contexto. */
    private int historyWindow = 12;

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public int getHistoryWindow() { return historyWindow; }
    public void setHistoryWindow(int historyWindow) { this.historyWindow = historyWindow; }
}
