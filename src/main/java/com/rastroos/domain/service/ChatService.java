package com.rastroos.domain.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Chat;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.ChatMessageRepository;
import com.rastroos.domain.repository.ChatRepository;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.ChatMessageDto;
import com.rastroos.web.dto.ChatSummaryDto;
import com.rastroos.web.dto.ManagerView;

/**
 * Regras de negócio das conversas com o Alfredo. Persiste conversas e
 * mensagens e orquestra a resposta da IA ({@link AlfredoAiClient}).
 *
 * <p>Isolamento estrito por {@code userId}: as mensagens não têm coluna de
 * usuário — o acesso é sempre feito via {@code chat} (verificado por
 * {@link ChatRepository#findByIdAndUserId}). Conversa de outro usuário → 404.
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
            "Oi! Sou o Alfredo, seu gerente financeiro. Pergunte qualquer coisa — "
            + "tenho acesso a todos os seus lançamentos, contas, investimentos e metas.";

    private final ChatRepository chats;
    private final ChatMessageRepository messages;
    private final AlfredoAiClient ai;

    public ChatService(ChatRepository chats,
                       ChatMessageRepository messages,
                       AlfredoAiClient ai) {
        this.chats = chats;
        this.messages = messages;
        this.ai = ai;
    }

    /**
     * Monta a tela: histórico + conversa ativa (se {@code activeChatId} for
     * dado). Conversa inexistente ou de outro usuário → 404.
     */
    @Transactional(readOnly = true)
    public ManagerView load(UUID userId, UUID activeChatId) {
        ChatDetailDto active = null;
        if (activeChatId != null) {
            active = toDetail(require(userId, activeChatId));
        }
        List<ChatSummaryDto> history = chats.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(c -> new ChatSummaryDto(
                        c.getId(), c.getTitle(), c.getCreatedAt(),
                        activeChatId != null && c.getId().equals(activeChatId)))
                .toList();
        return new ManagerView(history, active, SUGGESTIONS);
    }

    /** Abre uma conversa em branco (botão "Nova conversa"), só com a saudação. */
    @Transactional
    public UUID startEmpty(UUID userId) {
        Chat chat = new Chat();
        chat.setUserId(userId);
        chat.setTitle("Nova conversa");
        chats.save(chat);
        saveMessage(chat.getId(), ChatMessageRole.ASSISTANT, WELCOME);
        return chat.getId();
    }

    /** Abre uma nova conversa a partir da primeira mensagem e já responde. */
    @Transactional
    public UUID start(UUID userId, String message) {
        String text = message.strip();
        Chat chat = new Chat();
        chat.setUserId(userId);
        chat.setTitle(deriveTitle(text));
        chats.save(chat);
        exchange(chat.getId(), text);
        return chat.getId();
    }

    /** Envia nova mensagem numa conversa existente e responde. */
    @Transactional
    public ChatDetailDto send(UUID userId, UUID chatId, String message) {
        Chat chat = require(userId, chatId);
        exchange(chat.getId(), message.strip());
        return toDetail(chat);
    }

    @Transactional
    public void delete(UUID userId, UUID chatId) {
        Chat chat = require(userId, chatId);
        List<ChatMessage> msgs = messages.findAllByChatIdOrderByCreatedAtAsc(chatId);
        if (!msgs.isEmpty()) {
            messages.deleteAll(msgs);
        }
        chats.delete(chat);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Persiste a mensagem do usuário, pede a resposta ao Alfredo (com o
     * histórico anterior como contexto) e persiste a resposta.
     */
    private void exchange(UUID chatId, String userText) {
        List<ChatMessage> prior = messages.findAllByChatIdOrderByCreatedAtAsc(chatId);
        saveMessage(chatId, ChatMessageRole.USER, userText);
        String answer = ai.reply(userText, prior);
        saveMessage(chatId, ChatMessageRole.ASSISTANT, answer);
    }

    private Chat require(UUID userId, UUID chatId) {
        return chats.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("chat.notFound"));
    }

    private void saveMessage(UUID chatId, ChatMessageRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setChatId(chatId);
        m.setRole(role);
        m.setContent(content);
        messages.save(m);
    }

    private ChatDetailDto toDetail(Chat chat) {
        List<ChatMessageDto> msgs = messages.findAllByChatIdOrderByCreatedAtAsc(chat.getId()).stream()
                .map(m -> new ChatMessageDto(
                        m.getRole(), m.getContent(), m.getCreatedAt(),
                        m.getRole() == ChatMessageRole.ASSISTANT))
                .toList();
        return new ChatDetailDto(chat.getId(), chat.getTitle(), msgs);
    }

    private static String deriveTitle(String text) {
        String single = text.replaceAll("\\s+", " ").strip();
        if (single.isEmpty()) return "Nova conversa";
        return single.length() > TITLE_MAX
                ? single.substring(0, TITLE_MAX - 1).strip() + "…"
                : single;
    }
}
