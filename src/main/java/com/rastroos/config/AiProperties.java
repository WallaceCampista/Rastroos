package com.rastroos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração única da integração de IA do Rastroo$ (persona "Alfredo"):
 * chat, resumos de tela, embeddings da busca semântica e leitura de
 * documentos por visão.
 *
 * <p><strong>Independente de fornecedor.</strong> {@code provider} escolhe o
 * dialeto ({@code openai}, {@code gemini}, …) e todo o resto — URL, nomes de
 * modelo, dimensão dos vetores — cai no padrão desse fornecedor quando não é
 * informado. Trocar de motor é mudar variável de ambiente, não código:
 *
 * <pre>
 * AI_PROVIDER=gemini
 * AI_API_KEY=...
 * </pre>
 *
 * <p>Fica <strong>desligada</strong> enquanto não houver {@code api-key}: o app
 * responde em modo demonstração, sem tráfego externo.
 *
 * <p>A chave nunca é logada nem exposta em DTO/actuator.
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** Fornecedor: {@code openai}, {@code gemini} ou outro implementado. */
    private String provider = "openai";

    /** Raiz da API. Vazio = usa o padrão do fornecedor escolhido. */
    private String baseUrl = "";

    /** Chave de API enviada como Bearer (nunca logar). Vazia = modo stub. */
    private String apiKey = "";

    /** Modelo de texto/visão. Vazio = usa o padrão do fornecedor. */
    private String model = "";

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 30000;

    /** Tentativas extras em 429/5xx (respeita {@code Retry-After}). */
    private int maxRetries = 2;

    private final Chat chat = new Chat();
    private final Insight insight = new Insight();
    private final Embedding embedding = new Embedding();
    private final Vision vision = new Vision();
    private final Budget budget = new Budget();
    private final Warmup warmup = new Warmup();

    /**
     * {@code true} quando há credencial. A URL e os modelos têm padrão por
     * fornecedor; a chave, não — sem ela, chamar seria só colecionar 401 e
     * abrir o circuit breaker sem nunca ter funcionado.
     */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Raiz normalizada da API, com o padrão do fornecedor quando não
     * configurada. Tolera o valor legado que apontava direto para
     * {@code .../chat/completions}, para que uma configuração antiga continue
     * subindo em vez de montar URLs duplicadas.
     */
    public String resolvedBaseUrl(String providerDefault) {
        String url = baseUrl == null || baseUrl.isBlank() ? providerDefault : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        }
        return url;
    }

    /** Modelo configurado, ou o padrão do fornecedor. */
    public String resolvedModel(String providerDefault) {
        return model == null || model.isBlank() ? providerDefault : model.trim();
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public Chat getChat() { return chat; }
    public Insight getInsight() { return insight; }
    public Embedding getEmbedding() { return embedding; }
    public Vision getVision() { return vision; }
    public Budget getBudget() { return budget; }
    public Warmup getWarmup() { return warmup; }

    // ── Chat ─────────────────────────────────────────────────────────────

    /** Conversa com o Alfredo, ancorada nos dados reais do usuário. */
    public static class Chat {

        private int maxTokens = 700;

        /** Baixa de propósito: é resposta sobre dinheiro, não texto criativo. */
        private double temperature = 0.2;

        /** Quantas mensagens recentes do histórico seguem como contexto. */
        private int historyWindow = 12;

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getHistoryWindow() { return historyWindow; }
        public void setHistoryWindow(int historyWindow) { this.historyWindow = historyWindow; }
    }

    // ── Resumos de tela ──────────────────────────────────────────────────

    /** Balão flutuante do Alfredo em cada tela. */
    public static class Insight {

        private int maxTokens = 180;

        private double temperature = 0.3;

        /**
         * Versão do prompt. Subir invalida os resumos já persistidos sem apagar
         * linha nenhuma: o resumo salvo com versão diferente é regerado na
         * próxima varredura.
         */
        private int promptVersion = 2;

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getPromptVersion() { return promptVersion; }
        public void setPromptVersion(int promptVersion) { this.promptVersion = promptVersion; }
    }

    // ── Busca semântica ──────────────────────────────────────────────────

    /** Índice vetorial (pgvector) do texto livre do usuário. */
    public static class Embedding {

        private boolean enabled = true;

        /** Vazio = padrão do fornecedor. */
        private String model = "";

        /**
         * Precisa casar com {@code vector(N)} da tabela {@code ai_documents}.
         * {@code 0} = padrão do fornecedor. A checagem no boot
         * ({@code AiVectorDimensionCheck}) recusa subir com valor divergente,
         * em vez de deixar gravar vetor que o banco vai rejeitar.
         */
        private int dimensions = 0;

        private int batchSize = 64;

        /** Quantos trechos entram no contexto do chat. */
        private int topK = 8;

        /** Similaridade de cosseno mínima (0..1) para o trecho ser relevante. */
        private double minScore = 0.30;

        /** Teto de caracteres por documento indexado. */
        private int maxChars = 500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }

        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }

        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
    }

    // ── Visão ────────────────────────────────────────────────────────────

    /** Leitura de boleto/fatura/notinha pelo modelo multimodal. */
    public static class Vision {

        private boolean enabled = true;

        private int maxTokens = 500;

        /** {@code high} lê texto pequeno de notinha; {@code low} custa menos. */
        private String detail = "high";

        /** Defesa em profundidade além do limite do multipart. */
        private long maxFileSizeBytes = 8L * 1024 * 1024;

        /** Visão é mais lenta que texto: timeout próprio, maior. */
        private int readTimeoutMs = 45000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }

        public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
        public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    // ── Teto de consumo ──────────────────────────────────────────────────

    /** Limite diário por usuário, medido no livro-caixa {@code ai_usage}. */
    public static class Budget {

        private boolean enabled = true;

        private int dailyCallsPerUser = 200;

        private long dailyTokensPerUser = 300_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getDailyCallsPerUser() { return dailyCallsPerUser; }
        public void setDailyCallsPerUser(int dailyCallsPerUser) { this.dailyCallsPerUser = dailyCallsPerUser; }

        public long getDailyTokensPerUser() { return dailyTokensPerUser; }
        public void setDailyTokensPerUser(long dailyTokensPerUser) {
            this.dailyTokensPerUser = dailyTokensPerUser;
        }
    }

    // ── Pré-aquecimento ──────────────────────────────────────────────────

    /**
     * Regeração em lote disparada por mudança de dado. É o que tira a IA do
     * caminho do page load: a tela lê um resumo já pronto no banco.
     */
    public static class Warmup {

        private boolean enabled = true;

        /** Espera após a última escrita — agrupa rajadas numa geração só. */
        private int debounceSeconds = 20;

        private long sweepIntervalMs = 15000;

        private int maxUsersPerSweep = 20;

        /** Meses anteriores ao corrente que também são pré-aquecidos. */
        private int monthsBack = 1;

        /**
         * Espera depois de uma falha do provedor antes de tentar de novo.
         * Sem isso, um problema permanente (conta sem crédito, chave revogada)
         * viraria uma tentativa a cada varredura, para sempre.
         */
        private int failureBackoffSeconds = 300;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getDebounceSeconds() { return debounceSeconds; }
        public void setDebounceSeconds(int debounceSeconds) { this.debounceSeconds = debounceSeconds; }

        public long getSweepIntervalMs() { return sweepIntervalMs; }
        public void setSweepIntervalMs(long sweepIntervalMs) { this.sweepIntervalMs = sweepIntervalMs; }

        public int getMaxUsersPerSweep() { return maxUsersPerSweep; }
        public void setMaxUsersPerSweep(int maxUsersPerSweep) { this.maxUsersPerSweep = maxUsersPerSweep; }

        public int getMonthsBack() { return monthsBack; }
        public void setMonthsBack(int monthsBack) { this.monthsBack = monthsBack; }

        public int getFailureBackoffSeconds() { return failureBackoffSeconds; }
        public void setFailureBackoffSeconds(int failureBackoffSeconds) {
            this.failureBackoffSeconds = failureBackoffSeconds;
        }
    }
}
