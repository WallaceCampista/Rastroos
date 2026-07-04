package com.rastroos.web.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.service.InvestmentService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.InvestmentDto;
import com.rastroos.web.dto.InvestmentHistoryEntryDto;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.form.InvestmentForm;
import com.rastroos.web.form.InvestmentHistoryForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints REST de investimentos do usuário corrente.
 */
@RestController
@RequestMapping("/api/v1/investments")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Investments", description = "Cofrinhos e carteira do usuário corrente")
public class InvestmentRestController {

    private final CurrentUser currentUser;
    private final InvestmentService service;

    public InvestmentRestController(CurrentUser currentUser, InvestmentService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista cofrinhos + carteira + KPIs do usuário")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "View de investimentos"))
    public InvestmentsView list() {
        return service.load(currentUser.requireId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém um investimento por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encontrado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado ou pertence a outro usuário")
    })
    public InvestmentDto get(@PathVariable UUID id) {
        return service.get(currentUser.requireId(), id);
    }

    @PostMapping
    @Operation(summary = "Cria um novo investimento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Criado"),
            @ApiResponse(responseCode = "400", description = "Validação falhou")
    })
    public ResponseEntity<InvestmentDto> create(@Valid @RequestBody InvestmentForm form) {
        UUID userId = currentUser.requireId();
        Investment created = service.create(userId, form);
        return ResponseEntity.ok(service.get(userId, created.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um investimento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado"),
            @ApiResponse(responseCode = "400", description = "Validação falhou"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public InvestmentDto update(@PathVariable UUID id, @Valid @RequestBody InvestmentForm form) {
        UUID userId = currentUser.requireId();
        service.update(userId, id, form);
        return service.get(userId, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um investimento (e seu histórico mensal)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(currentUser.requireId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Histórico mensal do investimento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pontos (yearMonth, amount)"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public List<InvestmentHistoryEntryDto> history(@PathVariable UUID id) {
        return service.getHistory(currentUser.requireId(), id);
    }

    @PostMapping("/{id}/history")
    @Operation(summary = "Adiciona ou atualiza um ponto mensal no histórico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upsert realizado"),
            @ApiResponse(responseCode = "400", description = "Validação falhou"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public InvestmentHistoryEntryDto upsertHistory(@PathVariable UUID id,
                                                   @Valid @RequestBody InvestmentHistoryForm form) {
        InvestmentHistory snapshot = service.upsertHistory(currentUser.requireId(), id, form);
        return new InvestmentHistoryEntryDto(
                snapshot.getYearMonth(),
                com.rastroos.web.dto.MoneyDto.fromCents(snapshot.getAmountCents()));
    }
}
