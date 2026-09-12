package com.rastroos.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.entity.enums.ChatMessageRole;

/**
 * A persona "Alfredo" sobre o {@link AiModelClient}: define as instruções de
 * sistema, monta a conversa e garante que toda falha vire uma resposta
 * aceitável em vez de um erro na tela.
 *
 * <p>O ponto central é o <strong>contrato anti-invenção</strong>: o dossiê com
 * os números reais do usuário entra como mensagem de sistema, e o modelo é
 * instruído a não citar nenhum valor fora dele. Isso é o que diferencia a
 * resposta de um chute bem escrito — sem dossiê, um modelo de linguagem produz
 * valores plausíveis e errados com a mesma confiança.
 *
 * <p>Circuit breakers separados por uso ({@code ai-chat}, {@code ai-insight}):
 * o resumo de tela cair não derruba a conversa, e vice-versa. Nenhum dos dois
 * métodos públicos lança: falha do provedor, breaker aberto, teto diário ou IA
 * desligada sempre viram um texto utilizável.
 */
@Component
public class AlfredoAiClient {

    private static final Logger log = LoggerFactory.getLogger(AlfredoAiClient.class);

    /**
     * Instrução de sistema da conversa. As regras sobre números vêm primeiro e
     * são explícitas de propósito: é a diferença entre uma resposta conferível
     * e uma invenção convincente sobre o dinheiro de alguém.
     */
    static final String CHAT_SYSTEM_PROMPT = """
            Você é o Alfredo, o gerente financeiro pessoal do Rastroo$.

            REGRAS INVIOLÁVEIS SOBRE NÚMEROS
            1. Todo valor, data, quantidade, percentual ou nome próprio que você citar precisa
               aparecer literalmente no bloco DADOS REAIS que acompanha esta conversa.
            2. É proibido estimar, deduzir, arredondar de cabeça ou completar lacuna com suposição.
            3. Se a pessoa perguntar sobre um período, conta ou item que não está nos DADOS REAIS,
               responda que não tem esse dado aqui e diga em qual tela do Rastroo$ ela encontra.
            4. Só some valores que estejam listados, e mostre as parcelas que você somou.
            5. Itens da seção "busca por semelhança" são pistas para localizar um registro:
               nunca some, conte ou trate esses itens como total.
            6. Na dúvida entre responder com um número incerto e dizer que não sabe, diga que não sabe.

            COMO RESPONDER
            - Português do Brasil, direto e acolhedor, em 2 a 6 frases curtas.
            - Texto corrido; no máximo uma lista curta quando ela realmente ajudar.
            - Sem markdown de título, sem negrito, sem emoji.
            - Nunca prometa rendimento, retorno garantido ou recomendação de investimento específico.
            - Termine com o próximo passo concreto, quando fizer sentido.
            """;

    /**
     * Instrução de sistema dos resumos de tela. Mais apertada que a do chat: o
     * espaço é o de um balão, e os números já chegam prontos do servidor.
     */
    static final String INSIGHT_SYSTEM_PROMPT = """
            Você é o Alfredo, gerente financeiro pessoal do Rastroo$.
            Escreva UM resumo curto (2 a 3 frases, no máximo 55 palavras) em português do Brasil
            sobre a situação atual da tela e o cuidado mais importante a tomar.
            Use SOMENTE os números fornecidos; nunca invente valores, percentuais ou datas.
            Fale direto com a pessoa, sem saudação, sem listas, sem markdown e sem prometer rendimentos.
            """;

    private final AiModelClient client;
    private final AiBudgetGuard budget;
    private final AiCircuitBreakers breakers;
    private final AiProperties props;

    public AlfredoAiClient(AiModelClient client, AiBudgetGuard budget,
                           AiCircuitBreakers breakers, AiProperties props) {
        this.client = client;
        this.budget = budget;
        this.breakers = breakers;
        this.props = props;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    /**
     * Resposta do Alfredo a {@code userMessage}, ancorada em {@code context}
     * (o dossiê com os dados reais) e no histórico recente da conversa.
     *
     * <p>Nunca lança: falha do provedor, teto diário ou IA desligada caem no
     * texto de contingência.
     */
    public String reply(UUID userId, String userMessage, List<ChatMessage> history, String context) {
        if (!client.isEnabled()) {
            return stubReply(userMessage);
        }
        try {
            budget.check(userId, AiFeature.CHAT);
            return breakers.call(AiCircuitBreakers.CHAT,
                    () -> askRemote(userId, userMessage, history, context));
        } catch (RuntimeException e) {
            return contingencyReply(e);
        }
    }

    /**
     * Mesma resposta de {@link #reply}, entregue em pedaços conforme o modelo
     * escreve. Devolve o texto completo — é ele que vai para o banco e para a
     * tela no evento final, então uma falha no meio do caminho não deixa
     * resposta pela metade persistida.
     *
     * <p>Também nunca lança: cai no texto de contingência como o irmão.
     */
    public String replyStreaming(UUID userId, String userMessage, List<ChatMessage> history,
                                 String context, Consumer<String> onDelta) {
        if (!client.isEnabled()) {
            return stubReply(userMessage);
        }
        try {
            budget.check(userId, AiFeature.CHAT);
            return breakers.call(AiCircuitBreakers.CHAT, () ->
                    client.chatStream(AiFeature.CHAT, userId,
                            chatMessages(userMessage, history, context),
                            props.getChat().getMaxTokens(),
                            props.getChat().getTemperature(),
                            onDelta).content());
        } catch (RuntimeException e) {
            return contingencyReply(e);
        }
    }

    private String askRemote(UUID userId, String userMessage, List<ChatMessage> history,
                             String context) {
        return client.chat(AiFeature.CHAT, userId,
                chatMessages(userMessage, history, context),
                props.getChat().getMaxTokens(), props.getChat().getTemperature(), null).content();
    }

    /** Prompt de sistema + dossiê + janela do histórico + a pergunta. */
    private List<Map<String, Object>> chatMessages(String userMessage, List<ChatMessage> history,
                                                   String context) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", CHAT_SYSTEM_PROMPT));
        if (context != null && !context.isBlank()) {
            messages.add(message("system", context));
        }

        int window = Math.max(0, props.getChat().getHistoryWindow());
        List<ChatMessage> recent = history.size() > window
                ? history.subList(history.size() - window, history.size())
                : history;
        for (ChatMessage m : recent) {
            String role = m.getRole() == ChatMessageRole.ASSISTANT ? "assistant" : "user";
            messages.add(message(role, m.getContent()));
        }
        messages.add(message("user", userMessage));
        return messages;
    }

    /**
     * Reescreve, na voz do Alfredo, o resumo de uma tela a partir dos números
     * que o servidor já calculou.
     *
     * <p>{@link Optional#empty()} quando não há IA, não há orçamento ou o
     * provedor falhou: o chamador usa o resumo local determinístico, montado a
     * partir dos mesmos números.
     */
    public Optional<String> summarize(UUID userId, String prompt) {
        if (!client.isEnabled()) {
            return Optional.empty();
        }
        try {
            budget.check(userId, AiFeature.INSIGHT);
            return Optional.of(breakers.call(AiCircuitBreakers.INSIGHT,
                    () -> summarizeRemote(userId, prompt)));
        } catch (RuntimeException e) {
            log.warn("Alfredo indisponível para resumo ({}), usando o resumo local", e.toString());
            return Optional.empty();
        }
    }

    private String summarizeRemote(UUID userId, String prompt) {
        List<Map<String, Object>> messages = List.of(
                message("system", INSIGHT_SYSTEM_PROMPT),
                message("user", prompt));
        return client.chat(AiFeature.INSIGHT, userId, messages,
                props.getInsight().getMaxTokens(), props.getInsight().getTemperature(), null)
                .content();
    }

    // ── Contingência ─────────────────────────────────────────────────────

    /**
     * Texto de contingência do chat. Distingue o teto diário de uma
     * indisponibilidade real: são problemas diferentes e o usuário não deve
     * receber a mesma desculpa para os dois.
     */
    String contingencyReply(RuntimeException e) {
        if (e instanceof AiBudgetExceededException) {
            log.info("Chat recusado por teto diário de IA");
            return "Você já usou o limite de conversas com IA de hoje. Amanhã o limite se renova. "
                    + "Enquanto isso, os números continuam todos disponíveis nas telas de "
                    + "Visão geral, Gastos e Relatórios.";
        }
        log.warn("Alfredo indisponível ({}), usando resposta de contingência", e.toString());
        return "No momento não consegui falar com o motor de IA. Enquanto isso, dê uma olhada "
                + "nos seus vencimentos do mês em Gastos e no saldo em Visão geral. "
                + "Tente novamente em alguns instantes.";
    }

    /**
     * Resposta de demonstração (sem IA configurada): acolhe a pergunta e
     * aponta para as telas do app. Determinística para facilitar os testes.
     */
    private String stubReply(String userMessage) {
        String trimmed = userMessage == null ? "" : userMessage.strip();
        String snippet = trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
        return "Olá! Sou o Alfredo, seu gerente financeiro. Estou em modo demonstração "
                + "(a IA ainda não foi conectada), mas já consigo te orientar.\n\n"
                + "Sobre \"" + snippet + "\": comece conferindo a Visão geral do mês, "
                + "priorize os vencimentos em aberto e registre suas receitas para o saldo ficar fiel. "
                + "Quando o motor de IA for configurado, respondo com análises personalizadas dos seus dados.";
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
