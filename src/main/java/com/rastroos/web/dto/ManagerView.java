package com.rastroos.web.dto;

import java.util.List;

/**
 * Estado da tela /app/manager: histórico de conversas na lateral, a conversa
 * ativa (ou {@code null} na tela de boas-vindas) e sugestões de perguntas.
 */
public record ManagerView(
        List<ChatSummaryDto> history,
        ChatDetailDto activeChat,
        List<String> suggestions
) {

    public boolean hasActiveChat() {
        return activeChat != null;
    }
}
