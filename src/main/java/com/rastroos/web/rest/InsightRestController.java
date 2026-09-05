package com.rastroos.web.rest;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.ChatService;
import com.rastroos.domain.service.ScreenInsightService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.InsightDto;
import com.rastroos.web.dto.InsightScreen;
import com.rastroos.web.form.ChatPromptForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Resumos do Alfredo para o widget flutuante: o texto do balão de cada tela e
 * a abertura de uma conversa a partir dele.
 *
 * <p>Os números vêm do <em>dono dos dados</em>
 * ({@link CurrentUser#requireEffectiveId()}, que respeita acessor e "ver
 * como"); a conversa é gravada na conta autenticada
 * ({@link CurrentUser#requireId()}), que é a mesma usada pela tela do Alfredo —
 * é assim que a pergunta feita aqui aparece no histórico de lá.
 */
@RestController
@RequestMapping("/api/v1/insights")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Insights", description = "Resumos do Alfredo por tela (widget flutuante)")
public class InsightRestController {

    private final CurrentUser currentUser;
    private final ScreenInsightService insights;
    private final ChatService chats;
    private final Clock clock;

    public InsightRestController(CurrentUser currentUser,
                                 ScreenInsightService insights,
                                 ChatService chats,
                                 Clock clock) {
        this.currentUser = currentUser;
        this.insights = insights;
        this.chats = chats;
        this.clock = clock;
    }

    @GetMapping("/{screen}")
    @Operation(summary = "Resumo da tela para o mês selecionado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumo gerado"),
        @ApiResponse(responseCode = "404", description = "Tela sem resumo")
    })
    public InsightDto insight(@PathVariable("screen") String screen,
                              @RequestParam(value = "ym", required = false) String ym) {
        InsightScreen target = requireScreen(screen);
        YearMonth period = parseOrCurrent(ym);
        if (currentUser.isMaskActive()) {
            return insights.maskedInsight(target, period);
        }
        return insights.insight(currentUser.requireEffectiveId(), target, period);
    }

    @PostMapping("/{screen}/chat")
    @Operation(summary = "Abre uma conversa com o Alfredo a partir do resumo da tela")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversa criada, com a resposta do Alfredo"),
        @ApiResponse(responseCode = "400", description = "Pergunta vazia ou longa demais"),
        @ApiResponse(responseCode = "404", description = "Tela sem resumo")
    })
    public ChatDetailDto ask(@PathVariable("screen") String screen,
                             @RequestParam(value = "ym", required = false) String ym,
                             @Valid @RequestBody ChatPromptForm form) {
        InsightScreen target = requireScreen(screen);
        YearMonth period = parseOrCurrent(ym);
        // O resumo é recalculado no servidor (e vem do cache): o cliente não
        // consegue forjar uma fala do Alfredo mandando o texto de volta.
        InsightDto insight = currentUser.isMaskActive()
                ? insights.maskedInsight(target, period)
                : insights.insight(currentUser.requireEffectiveId(), target, period);
        UUID ownerOfChats = currentUser.requireId();
        return chats.startFromScreen(ownerOfChats, target.label(), insight.text(), form.getMessage());
    }

    /** Tela desconhecida → 404 (o widget cai no modo "só chat"). */
    private static InsightScreen requireScreen(String screen) {
        return InsightScreen.parse(screen)
                .orElseThrow(() -> new ResourceNotFoundException("insight.screenNotFound"));
    }

    private YearMonth parseOrCurrent(String ym) {
        if (ym == null || ym.isBlank()) return YearMonth.now(clock);
        try {
            return YearMonth.parse(ym.trim());
        } catch (Exception e) {
            return YearMonth.now(clock);
        }
    }
}
