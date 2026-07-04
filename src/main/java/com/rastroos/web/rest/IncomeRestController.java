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

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.service.IncomeService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.form.IncomeForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints REST para receitas do usuário corrente.
 */
@RestController
@RequestMapping("/api/v1/incomes")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Incomes", description = "Receitas do usuário corrente")
public class IncomeRestController {

    private final CurrentUser currentUser;
    private final IncomeService service;
    private final Clock clock;

    public IncomeRestController(CurrentUser currentUser,
                                IncomeService service,
                                Clock clock) {
        this.currentUser = currentUser;
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Lista receitas do mês com filtros e paginação")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Página de receitas"))
    public IncomesPageView list(
            @RequestParam(value = "ym", required = false) String ym,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "q", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {

        YearMonth period = parseOrCurrent(ym);
        IncomeFilter filter = new IncomeFilter(categoryId, search);
        return service.listForMonth(currentUser.requireId(), period, filter, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém uma receita por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encontrada"),
            @ApiResponse(responseCode = "404", description = "Não encontrada ou pertence a outro usuário")
    })
    public IncomeDto get(@PathVariable UUID id) {
        return service.get(currentUser.requireId(), id);
    }

    @PostMapping
    @Operation(summary = "Cria uma nova receita")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Criada"),
            @ApiResponse(responseCode = "400", description = "Validação falhou")
    })
    public ResponseEntity<IncomeDto> create(@Valid @RequestBody IncomeForm form) {
        UUID userId = currentUser.requireId();
        Income created = service.create(userId, form);
        return ResponseEntity.ok(service.get(userId, created.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma receita")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizada"),
            @ApiResponse(responseCode = "400", description = "Validação falhou"),
            @ApiResponse(responseCode = "404", description = "Não encontrada")
    })
    public IncomeDto update(@PathVariable UUID id,
                            @Valid @RequestBody IncomeForm form) {
        UUID userId = currentUser.requireId();
        service.update(userId, id, form);
        return service.get(userId, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove uma receita")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Não encontrada")
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
