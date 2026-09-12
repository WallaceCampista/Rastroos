package com.rastroos.domain.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rastroos.domain.entity.Chat;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.ChatSummaryDto;
import com.rastroos.web.dto.ManagerView;

/**
 * Regras das conversas com o Alfredo: persiste a thread e orquestra a resposta
 * da IA <strong>ancorada nos dados reais do usuário</strong>.
 *
 * <p>Antes de cada resposta, monta um dossiê com os números do dono dos dados
 * ({@link FinancialContextBuilder}) e o completa com os registros que mais se
 * parecem com a pergunta ({@link SemanticSearchService}). É esse par que
 * sustenta a promessa de resposta conferível: o modelo não busca nada sozinho
 * e não pode citar valor que não esteja no dossiê.
 *
 * <p>Deliberadamente <strong>sem {@code @Transactional}</strong>: a persistência
 * fica em {@link ChatStore}, em transações curtas, e a chamada à IA acontece
 * fora delas (ver o javadoc do store).
 *
 * <p>Isolamento por usuário em {@link ChatScope}: a conversa é gravada na conta
 * autenticada e os números vêm do dono dos dados — conversa de outro usuário
 * resulta em 404.
 */
@Service
public class ChatService {

    /** Tamanho máximo do título derivado da primeira mensagem. */
    public static final int TITLE_MAX = 60;

    private static final List<String> SUGGESTIONS = List.of(
            "Posso assumir uma nova parcela de R$ 400 sem comprometer o orçamento?",
            "Como estão indo meus gastos comparado ao mês passado?",
            "Onde posso cortar gastos para economizar mais?",
            "Quanto tempo até eu atingir minha meta do casamento?",
            "Quais contas eu ainda preciso pagar este mês?",
            "Qual cartão tem a maior fatura? E o melhor parcelamento?");

    /** Saudação inicial do Alfredo numa conversa em branco. */
    private static final String WELCOME =
            "Oi! Sou o Alfredo, seu gerente financeiro. Pergunte o que quiser — eu leio seus "
            + "lançamentos, contas, receitas e investimentos dos últimos 12 meses antes de "
            + "responder, e digo quando um dado não está comigo.";

    /**
     * Aviso usado no lugar do dossiê quando não há acesso aos números (acessor
     * com valores mascarados). Entra como contexto para o modelo não tentar
     * responder com número nenhum.
     */
    private static final String NO_DATA_CONTEXT =
            "DADOS REAIS: indisponíveis. O titular optou por ocultar os valores desta conta.\n"
            + "Você NÃO tem acesso a nenhum valor, saldo, lançamento ou data. "
            + "Se perguntarem qualquer número, explique que os valores estão ocultos e "
            + "ofereça ajuda com organização, prazos e prioridades.";

    private final ChatStore store;
    private final AlfredoAiClient ai;
    private final FinancialContextBuilder contextBuilder;
    private final SemanticSearchService semantic;

    public ChatService(ChatStore store,
                       AlfredoAiClient ai,
                       FinancialContextBuilder contextBuilder,
                       SemanticSearchService semantic) {
        this.store = store;
        this.ai = ai;
        this.contextBuilder = contextBuilder;
        this.semantic = semantic;
    }

    /**
     * Monta a tela: histórico + conversa ativa (se {@code activeChatId} for
     * dado). Conversa inexistente ou de outro usuário → 404.
     */
    public ManagerView load(UUID ownerId, UUID activeChatId) {
        ChatDetailDto active = activeChatId == null
                ? null
                : store.detail(store.require(ownerId, activeChatId));
        List<ChatSummaryDto> history = store.historyOf(ownerId).stream()
                .map(c -> new ChatSummaryDto(
                        c.getId(), c.getTitle(), c.getCreatedAt(),
                        activeChatId != null && c.getId().equals(activeChatId)))
                .toList();
        return new ManagerView(history, active, SUGGESTIONS);
    }

    /** Abre uma conversa em branco (botão "Nova conversa"), só com a saudação. */
    public UUID startEmpty(UUID ownerId) {
        Chat chat = store.create(ownerId, "Nova conversa");
        store.append(chat.getId(), ChatMessageRole.ASSISTANT, WELCOME);
        return chat.getId();
    }

    /** Abre uma nova conversa a partir da primeira mensagem e já responde. */
    public UUID start(ChatScope scope, String message) {
        return open(scope, null, null, message).id();
    }

    /**
     * Abre uma conversa a partir do resumo de uma tela (widget flutuante): o
     * resumo entra como primeira fala do Alfredo e vira contexto da resposta,
     * e a pergunta do usuário segue logo depois.
     *
     * <p>Como é uma conversa comum, ela aparece no histórico da tela do Alfredo
     * igual às demais — só o título ganha o prefixo da tela de origem.
     */
    public ChatDetailDto startFromScreen(ChatScope scope, String screenLabel,
                                         String screenSummary, String question) {
        return open(scope, screenLabel, screenSummary, question);
    }

    /**
     * Abre uma conversa a partir de uma pergunta avulsa e devolve a thread —
     * mesmo fluxo de {@link #start}, em JSON, para o chat flutuante das telas
     * que não têm resumo.
     */
    public ChatDetailDto startAndDetail(ChatScope scope, String question) {
        return open(scope, null, null, question);
    }

    /** Envia nova mensagem numa conversa existente e responde. */
    public ChatDetailDto send(ChatScope scope, UUID chatId, String message) {
        Chat chat = store.require(scope.chatOwnerId(), chatId);
        exchange(scope, chat.getId(), message.strip());
        return store.detail(chat);
    }

    public void delete(UUID ownerId, UUID chatId) {
        store.delete(ownerId, chatId);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private ChatDetailDto open(ChatScope scope, String titlePrefix, String seed, String question) {
        String text = question.strip();
        Chat chat = store.create(scope.chatOwnerId(), deriveTitle(titlePrefix, text));
        if (seed != null && !seed.isBlank()) {
            store.append(chat.getId(), ChatMessageRole.ASSISTANT, seed);
        }
        exchange(scope, chat.getId(), text);
        return store.detail(chat);
    }

    /**
     * Persiste a pergunta, monta o dossiê, pede a resposta e persiste o
     * retorno. A ordem importa: a pergunta é gravada antes da chamada à IA,
     * então mesmo que o provedor caia o que a pessoa escreveu não se perde.
     */
    private void exchange(ChatScope scope, UUID chatId, String userText) {
        List<ChatMessage> prior = store.messagesOf(chatId);
        store.append(chatId, ChatMessageRole.USER, userText);

        String context = buildContext(scope, userText);
        String answer = ai.reply(scope.chatOwnerId(), userText, prior, context);

        store.append(chatId, ChatMessageRole.ASSISTANT, answer);
    }

    /**
     * Dossiê da pergunta: números exatos do dono dos dados + registros
     * semelhantes à pergunta. Sem acesso aos valores, devolve só o aviso de
     * mascaramento.
     */
    private String buildContext(ChatScope scope, String question) {
        if (!scope.hasData()) {
            return NO_DATA_CONTEXT;
        }
        UUID dataOwner = scope.dataOwnerId();
        String facts = contextBuilder.build(dataOwner, scope.period());
        String similar = semantic.asPromptSection(semantic.search(dataOwner, question));
        return similar.isEmpty() ? facts : facts + similar;
    }

    /** Título derivado da mensagem, opcionalmente prefixado pela tela de origem. */
    private static String deriveTitle(String prefix, String text) {
        String single = text.replaceAll("\\s+", " ").strip();
        if (single.isEmpty()) return "Nova conversa";
        String full = (prefix == null || prefix.isBlank()) ? single : prefix.strip() + " · " + single;
        return full.length() > TITLE_MAX
                ? full.substring(0, TITLE_MAX - 1).strip() + "…"
                : full;
    }
}
