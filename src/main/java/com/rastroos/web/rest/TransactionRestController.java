package com.rastroos.web.rest;

import java.time.Clock;
import java.time.YearMonth;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rastroos.domain.service.TransactionService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.TransactionDto;
import com.rastroos.web.dto.TransactionFilter;
import com.rastroos.web.dto.TransactionsPageView;
import com.rastroos.web.form.TransactionForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints REST para gestão de lançamentos (despesas) do usuário corrente.
 * Filtros e paginação espelham a tela /app/expenses.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
@Tag(name = "Transactions", description = "Lançamentos (despesas) do usuário corrente")
public class TransactionRestController {

    private final CurrentUser currentUser;
    private final TransactionService service;
    private final Clock clock;

    public TransactionRestController(CurrentUser currentUser,
                                     TransactionService service,
                                     Clock clock) {
        this.currentUser = currentUser;
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Lista lançamentos do mês, com filtros e paginação")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Página de lançamentos"))
    public TransactionsPageView list(
            @RequestParam(value = "ym", required = false) String ym,
            @RequestParam(value = "paid", required = false) String paid,
            @RequestParam(value = "fixed", required = false) String fixed,
            @RequestParam(value = "accountId", required = false) UUID accountId,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "q", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {

        YearMonth period = parseOrCurrent(ym);
        TransactionFilter filter = new TransactionFilter(
                TransactionFilter.PaidFilter.parse(paid),
                accountId,
                categoryId,
                TransactionFilter.FixedFilter.parse(fixed),
                search
        );
        return service.listForMonth(currentUser.requireId(), period, filter, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém um lançamento por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encontrado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado ou pertence a outro usuário")
    })
    public TransactionDto get(@PathVariable UUID id) {
        return service.get(currentUser.requireId(), id);
    }

    @PostMapping
    @Operation(summary = "Cria um lançamento (com suporte a parcelas)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Criado"),
            @ApiResponse(responseCode = "400", description = "Validação falhou")
    })
    public ResponseEntity<TransactionDto> create(@Valid @RequestBody TransactionForm form) {
        UUID userId = currentUser.requireId();
        UUID firstId = service.create(userId, form).get(0).getId();
        return ResponseEntity.ok(service.get(userId, firstId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um lançamento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado"),
            @ApiResponse(responseCode = "400", description = "Validação falhou"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public TransactionDto update(@PathVariable UUID id,
                                 @Valid @RequestBody TransactionForm form) {
        UUID userId = currentUser.requireId();
        service.update(userId, id, form);
        return service.get(userId, id);
    }

    @PostMapping("/{id}/toggle-paid")
    @Operation(summary = "Alterna o estado pago/não pago do lançamento")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Alternado"))
    public TransactionDto togglePaid(@PathVariable UUID id) {
        UUID userId = currentUser.requireId();
        service.togglePaid(userId, id);
        return service.get(userId, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um lançamento")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(currentUser.requireId(), id);
        return ResponseEntity.noContent().build();
    }

    private YearMonth parseOrCurrent(String ym) {
        if (ym == null || ym.isBlank()) return YearMonth.now(clock);
        try {
            return YearMonth.parse(ym);
        } catch (Exception e) {
            return YearMonth.now(clock);
        }
    }
}
