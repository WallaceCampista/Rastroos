package com.rastroos.web.rest;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.rastroos.domain.service.ChatScope;
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
/*
 * Acesso ao Alfredo é liberado conta a conta por um administrador
 * (users.ai_enabled). Sem ele, a rota responde 403 — esconder o orbe e o item
 * de menu é conforto, não segurança: quem digitasse a URL entraria.
 */
@PreAuthorize("isAuthenticated() and @currentUser.hasAiAccess()")
@Tag(name = "Chats", description = "Conversas com o Alfredo")
public class ChatRestController {

    private static final Logger log = LoggerFactory.getLogger(ChatRestController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CurrentUser currentUser;
    private final ChatService service;
    private final Clock clock;

    public ChatRestController(CurrentUser currentUser, ChatService service, Clock clock) {
        this.currentUser = currentUser;
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Abre uma conversa a partir de uma pergunta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversa criada, com a resposta do Alfredo"),
        @ApiResponse(responseCode = "400", description = "Mensagem vazia ou longa demais")
    })
    public ChatDetailDto start(@Valid @RequestBody ChatPromptForm form) {
        return service.startAndDetail(scope(), form.getMessage());
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
        return service.send(scope(), id, form.getMessage());
    }

    /**
     * Mesma mensagem de {@link #send}, com a resposta chegando em pedaços
     * (SSE) para a tela ir escrevendo enquanto o modelo pensa.
     *
     * <p>Três eventos: {@code delta} (um trecho), {@code done} (o texto
     * completo, que é o autoritativo — a tela re-renderiza com ele, então uma
     * falha no meio nunca deixa resposta pela metade) e {@code error}.
     *
     * <p>É POST porque manda corpo; o cliente lê com {@code fetch} +
     * {@code ReadableStream}, não com {@code EventSource}. Quem não tem JS
     * continua pelo POST + redirect de /app/manager.
     */
    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Envia uma mensagem e recebe a resposta em streaming (SSE)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fluxo de eventos com a resposta"),
        @ApiResponse(responseCode = "400", description = "Mensagem vazia ou longa demais"),
        @ApiResponse(responseCode = "404", description = "Conversa inexistente ou de outro usuário")
    })
    public ResponseEntity<StreamingResponseBody> sendStreaming(@PathVariable("id") UUID id,
                                                               @Valid @RequestBody ChatPromptForm form) {
        // O escopo é resolvido AQUI, na thread da requisição: o corpo do
        // streaming roda em despacho assíncrono, onde o SecurityContext já não
        // está mais garantido.
        ChatScope scope = scope();
        String message = form.getMessage();

        StreamingResponseBody body = out -> {
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            try {
                ChatDetailDto detail = service.sendStreaming(scope, id, message,
                        delta -> emit(writer, "delta", delta));
                emit(writer, "done", lastAssistantText(detail));
            } catch (RuntimeException e) {
                log.warn("Streaming do chat interrompido: {}", e.toString());
                emit(writer, "error", "");
            } finally {
                flushQuietly(writer);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-store")
                // Sem isto um proxy que bufferiza entrega tudo de uma vez no
                // fim — que é exatamente o que o streaming existe para evitar.
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    /** Um evento SSE. O payload vai como JSON: {@code data:} não aceita quebra de linha crua. */
    private static void emit(Writer writer, String event, String text) {
        try {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("text", text == null ? "" : text);
            writer.write("event: " + event + "\n");
            writer.write("data: " + MAPPER.writeValueAsString(payload) + "\n\n");
            writer.flush();
        } catch (IOException e) {
            // Cliente fechou a aba no meio: interrompe o fluxo sem barulho.
            throw new StreamClosedException(e);
        }
    }

    private static void flushQuietly(Writer writer) {
        try {
            writer.flush();
        } catch (IOException ignored) {
            // Conexão já encerrada pelo cliente.
        }
    }

    private static String lastAssistantText(ChatDetailDto detail) {
        var messages = detail.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).assistant()) {
                return messages.get(i).content();
            }
        }
        return "";
    }

    /** Cliente desconectou; não é erro de aplicação. */
    private static final class StreamClosedException extends RuntimeException {
        StreamClosedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Conversa na conta autenticada, números do dono dos dados — e nenhum
     * número quando o titular mascarou os valores para o acessor.
     */
    private ChatScope scope() {
        return currentUser.chatScope(YearMonth.now(clock));
    }
}
