package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.Chat;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre com Postgres real as conversas do Alfredo: histórico por usuário
 * (ordem desc, isolamento) e o thread de mensagens (ordem cronológica).
 */
class ChatRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private ChatRepository chats;
    @Autowired private ChatMessageRepository messages;

    @Test
    void historicoPorUsuarioEmOrdemDescSemVazar() {
        User alice = users.saveAndFlush(newUser("a-chat@example.com"));
        User bob = users.saveAndFlush(newUser("b-chat@example.com"));

        chats.saveAndFlush(chat(alice, "Antiga", Instant.parse("2026-05-01T10:00:00Z")));
        chats.saveAndFlush(chat(alice, "Recente", Instant.parse("2026-05-10T10:00:00Z")));
        chats.saveAndFlush(chat(bob, "Do Bob", Instant.parse("2026-05-09T10:00:00Z")));

        List<Chat> aliceChats = chats.findAllByUserIdOrderByCreatedAtDesc(alice.getId());

        assertThat(aliceChats).extracting(Chat::getTitle).containsExactly("Recente", "Antiga");
    }

    @Test
    void findByIdAndUserIdIsolaEntreUsuarios() {
        User alice = users.saveAndFlush(newUser("a-chat-iso@example.com"));
        User bob = users.saveAndFlush(newUser("b-chat-iso@example.com"));
        Chat aliceChat = chats.saveAndFlush(chat(alice, "Da Alice", Instant.now()));

        assertThat(chats.findByIdAndUserId(aliceChat.getId(), alice.getId())).isPresent();
        assertThat(chats.findByIdAndUserId(aliceChat.getId(), bob.getId())).isEmpty();
    }

    @Test
    void threadDeMensagensEmOrdemCronologica() {
        User alice = users.saveAndFlush(newUser("a-chat-msg@example.com"));
        Chat chat = chats.saveAndFlush(chat(alice, "Conversa", Instant.now()));

        messages.saveAndFlush(message(chat.getId(), ChatMessageRole.USER,
                "primeira", Instant.parse("2026-05-01T10:00:00Z")));
        messages.saveAndFlush(message(chat.getId(), ChatMessageRole.ASSISTANT,
                "segunda", Instant.parse("2026-05-01T10:00:05Z")));
        messages.saveAndFlush(message(chat.getId(), ChatMessageRole.USER,
                "terceira", Instant.parse("2026-05-01T10:00:10Z")));

        List<ChatMessage> thread = messages.findAllByChatIdOrderByCreatedAtAsc(chat.getId());

        assertThat(thread).extracting(ChatMessage::getContent)
                .containsExactly("primeira", "segunda", "terceira");
        assertThat(thread.get(0).getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(thread.get(1).getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
    }

    // ── helpers ──────────────────────────────────────────────

    private static User newUser(String email) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private static Chat chat(User owner, String title, Instant createdAt) {
        Chat c = new Chat();
        c.setUserId(owner.getId());
        c.setTitle(title);
        c.setCreatedAt(createdAt);
        return c;
    }

    private static ChatMessage message(UUID chatId, ChatMessageRole role,
                                       String content, Instant at) {
        ChatMessage m = new ChatMessage();
        m.setChatId(chatId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(at);
        return m;
    }
}
