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

/**
 * Persistência transacional das conversas, isolada do {@link ChatService}.
 *
 * <p><strong>Por que é um bean separado</strong>: a chamada HTTP ao provedor de
 * IA leva segundos. Se ela acontecesse dentro do {@code @Transactional} do
 * service, cada pergunta seguraria uma conexão do pool durante toda a espera —
 * dez perguntas simultâneas esgotariam o pool inteiro e derrubariam o resto do
 * app. Com a persistência aqui, cada transação é curta e a espera pela IA
 * acontece fora de qualquer uma delas. (Métodos no próprio service não
 * serviriam: chamada interna não passa pelo proxy do Spring.)
 *
 * <p>Isolamento por usuário: mensagens não têm coluna de usuário, o acesso é
 * sempre via {@code chat} verificado por {@link ChatRepository#findByIdAndUserId}
 * — conversa de outro usuário resulta em 404 (§2.2).
 */
@Service
public class ChatStore {

    private final ChatRepository chats;
    private final ChatMessageRepository messages;

    public ChatStore(ChatRepository chats, ChatMessageRepository messages) {
        this.chats = chats;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public Chat require(UUID ownerId, UUID chatId) {
        return chats.findByIdAndUserId(chatId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("chat.notFound"));
    }

    @Transactional
    public Chat create(UUID ownerId, String title) {
        Chat chat = new Chat();
        chat.setUserId(ownerId);
        chat.setTitle(title);
        return chats.save(chat);
    }

    @Transactional(readOnly = true)
    public List<Chat> historyOf(UUID ownerId) {
        return chats.findAllByUserIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> messagesOf(UUID chatId) {
        return messages.findAllByChatIdOrderByCreatedAtAsc(chatId);
    }

    @Transactional
    public void append(UUID chatId, ChatMessageRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setChatId(chatId);
        m.setRole(role);
        m.setContent(content);
        messages.save(m);
    }

    @Transactional
    public void delete(UUID ownerId, UUID chatId) {
        Chat chat = require(ownerId, chatId);
        List<ChatMessage> msgs = messages.findAllByChatIdOrderByCreatedAtAsc(chatId);
        if (!msgs.isEmpty()) {
            messages.deleteAll(msgs);
        }
        chats.delete(chat);
    }

    @Transactional(readOnly = true)
    public ChatDetailDto detail(Chat chat) {
        List<ChatMessageDto> msgs = messagesOf(chat.getId()).stream()
                .map(m -> new ChatMessageDto(
                        m.getRole(), m.getContent(), m.getCreatedAt(),
                        m.getRole() == ChatMessageRole.ASSISTANT))
                .toList();
        return new ChatDetailDto(chat.getId(), chat.getTitle(), msgs);
    }
}
