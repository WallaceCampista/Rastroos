package com.rastroos.web.rest;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rastroos.domain.service.ChatService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.form.ChatPromptForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Continuação de uma conversa com o Alfredo sem recarregar a página — usado
 * pelo chat flutuante do widget. A tela /app/manager continua funcionando por
 * POST + redirect (funciona sem JS); este endpoint é o mesmo fluxo em JSON.
 *
 * <p>Conversa de outro usuário → 404 pelo {@link ChatService} (§2.2).
 */
@RestController
@RequestMapping("/api/v1/chats")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Chats", description = "Conversas com o Alfredo")
public class ChatRestController {

    private final CurrentUser currentUser;
    private final ChatService service;

    public ChatRestController(CurrentUser currentUser, ChatService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Abre uma conversa a partir de uma pergunta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversa criada, com a resposta do Alfredo"),
        @ApiResponse(responseCode = "400", description = "Mensagem vazia ou longa demais")
    })
    public ChatDetailDto start(@Valid @RequestBody ChatPromptForm form) {
        return service.startAndDetail(currentUser.requireId(), form.getMessage());
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Envia uma mensagem numa conversa existente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversa atualizada com a resposta"),
        @ApiResponse(responseCode = "400", description = "Mensagem vazia ou longa demais"),
        @ApiResponse(responseCode = "404", description = "Conversa inexistente ou de outro usuário")
    })
    public ChatDetailDto send(@PathVariable("id") UUID id,
                              @Valid @RequestBody ChatPromptForm form) {
        return service.send(currentUser.requireId(), id, form.getMessage());
    }
}
