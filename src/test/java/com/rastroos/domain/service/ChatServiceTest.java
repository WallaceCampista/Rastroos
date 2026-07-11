package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Chat;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.ChatMessageRepository;
import com.rastroos.domain.repository.ChatRepository;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.ManagerView;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatRepository chats;
    @Mock private ChatMessageRepository messages;
    @Mock private AlfredoAiClient ai;

    @InjectMocks private ChatService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void startCriaConversaPersisteParDeMensagensEDerivaTitulo() {
        when(chats.save(any(Chat.class))).thenAnswer(inv -> {
            Chat c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(messages.findAllByChatIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(ai.reply(eq("Quanto gastei?"), anyList())).thenReturn("Você gastou R$ 1.234.");
        when(messages.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID chatId = service.start(alice, "  Quanto gastei?  ");

        assertThat(chatId).isNotNull();

        ArgumentCaptor<Chat> chatCaptor = ArgumentCaptor.forClass(Chat.class);
        verify(chats).save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getUserId()).isEqualTo(alice);
        assertThat(chatCaptor.getValue().getTitle()).isEqualTo("Quanto gastei?");

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, times(2)).save(msgCaptor.capture());
        List<ChatMessage> saved = msgCaptor.getAllValues();
        assertThat(saved.get(0).getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(saved.get(0).getContent()).isEqualTo("Quanto gastei?");
        assertThat(saved.get(1).getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(saved.get(1).getContent()).isEqualTo("Você gastou R$ 1.234.");
    }

    @Test
    void startComTituloLongoTrunca() {
        when(chats.save(any(Chat.class))).thenAnswer(inv -> {
            Chat c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(messages.findAllByChatIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(ai.reply(any(), anyList())).thenReturn("ok");
        when(messages.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        String longMsg = "palavra ".repeat(30);
        service.start(alice, longMsg);

        ArgumentCaptor<Chat> chatCaptor = ArgumentCaptor.forClass(Chat.class);
        verify(chats).save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getTitle()).endsWith("…");
        assertThat(chatCaptor.getValue().getTitle().length()).isLessThanOrEqualTo(ChatService.TITLE_MAX);
    }

    @Test
    void sendEmConversaDeOutroUsuarioRetorna404() {
        when(chats.findByIdAndUserId(any(), eq(alice))).thenReturn(Optional.empty());

        UUID chatId = UUID.randomUUID();
        assertThatThrownBy(() -> service.send(alice, chatId, "oi"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ai, never()).reply(any(), anyList());
    }

    @Test
    void sendAnexaParDeMensagensComHistoricoComoContexto() {
        UUID chatId = UUID.randomUUID();
        Chat chat = chat(chatId, alice, "Minha conversa");
        when(chats.findByIdAndUserId(chatId, alice)).thenReturn(Optional.of(chat));

        List<ChatMessage> prior = List.of(
                message(chatId, ChatMessageRole.USER, "oi"),
                message(chatId, ChatMessageRole.ASSISTANT, "olá"));
        when(messages.findAllByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(prior);
        when(ai.reply(eq("e agora?"), eq(prior))).thenReturn("resposta");
        when(messages.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatDetailDto detail = service.send(alice, chatId, "e agora?");

        assertThat(detail.id()).isEqualTo(chatId);
        verify(ai).reply(eq("e agora?"), eq(prior));
        verify(messages, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void loadComConversaAtivaMarcaHistoricoEDevolveMensagens() {
        UUID chatId = UUID.randomUUID();
        Chat chat = chat(chatId, alice, "Conversa A");
        when(chats.findByIdAndUserId(chatId, alice)).thenReturn(Optional.of(chat));
        when(messages.findAllByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(List.of(
                message(chatId, ChatMessageRole.USER, "oi"),
                message(chatId, ChatMessageRole.ASSISTANT, "olá")));
        when(chats.findAllByUserIdOrderByCreatedAtDesc(alice)).thenReturn(List.of(chat));

        ManagerView view = service.load(alice, chatId);

        assertThat(view.hasActiveChat()).isTrue();
        assertThat(view.activeChat().messages()).hasSize(2);
        assertThat(view.activeChat().messages().get(1).assistant()).isTrue();
        assertThat(view.history()).hasSize(1);
        assertThat(view.history().get(0).active()).isTrue();
        assertThat(view.suggestions()).isNotEmpty();
    }

    @Test
    void loadSemConversaAtivaMostraBoasVindas() {
        when(chats.findAllByUserIdOrderByCreatedAtDesc(alice)).thenReturn(List.of());

        ManagerView view = service.load(alice, null);

        assertThat(view.hasActiveChat()).isFalse();
        assertThat(view.history()).isEmpty();
    }

    @Test
    void loadDeConversaDeOutroUsuarioRetorna404() {
        UUID chatId = UUID.randomUUID();
        when(chats.findByIdAndUserId(chatId, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.load(bob, chatId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemoveMensagensEConversa() {
        UUID chatId = UUID.randomUUID();
        Chat chat = chat(chatId, alice, "X");
        when(chats.findByIdAndUserId(chatId, alice)).thenReturn(Optional.of(chat));
        List<ChatMessage> msgs = List.of(message(chatId, ChatMessageRole.USER, "oi"));
        when(messages.findAllByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(msgs);

        service.delete(alice, chatId);

        verify(messages).deleteAll(msgs);
        verify(chats).delete(chat);
    }

    // ── helpers ──────────────────────────────────────────────

    private static Chat chat(UUID id, UUID userId, String title) {
        Chat c = new Chat();
        c.setId(id);
        c.setUserId(userId);
        c.setTitle(title);
        c.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        return c;
    }

    private static ChatMessage message(UUID chatId, ChatMessageRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setChatId(chatId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(Instant.parse("2026-05-01T10:05:00Z"));
        return m;
    }
}
