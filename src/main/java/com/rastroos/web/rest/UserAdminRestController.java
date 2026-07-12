package com.rastroos.web.rest;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.service.UserAdminService;
import com.rastroos.domain.service.UserAdminService.CreateResult;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.UserAdminListView;
import com.rastroos.web.dto.UserDetailView;
import com.rastroos.web.form.UserCreateForm;
import com.rastroos.web.form.UserEditForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints REST de administração de usuários. Restrito a ADMIN tanto pela
 * regra de URL em {@code SecurityConfig} quanto por {@code @PreAuthorize}.
 * Nunca retorna a entidade {@code User} — só DTOs sem dados sensíveis.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · Users", description = "Gestão de usuários (admin)")
public class UserAdminRestController {

    private final CurrentUser currentUser;
    private final UserAdminService service;

    public UserAdminRestController(CurrentUser currentUser, UserAdminService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    /** Corpo da resposta do reset de senha (senha temporária de uso único). */
    @Schema(description = "Senha temporária gerada no reset (mostrada uma única vez)")
    public record TempPasswordResponse(
            @Schema(description = "Senha temporária forte; o usuário deverá trocá-la no próximo login",
                    example = "Xk7#pQ2m!aBcd")
            String temporaryPassword) {
    }

    @GetMapping
    @Operation(summary = "Lista usuários com filtros de status/busca e paginação")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Página de usuários"))
    public UserAdminListView list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return service.list(status, q, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um usuário (sessões ativas + histórico de login)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encontrado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public UserDetailView get(@PathVariable UUID id) {
        return service.detail(id, currentUser.requireId());
    }

    @PostMapping
    @Operation(summary = "Cria um usuário (senha inicial exige troca no 1º login)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Criado"),
            @ApiResponse(responseCode = "409", description = "Email já em uso"),
            @ApiResponse(responseCode = "422", description = "Senha não atende à política")
    })
    public ResponseEntity<?> create(@Valid @RequestBody UserCreateForm form) {
        CreateResult result = service.create(form);
        if (!result.ok()) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("code", "WEAK_PASSWORD", "errors", result.passwordErrors()));
        }
        User u = result.user();
        return ResponseEntity.ok(service.detail(u.getId(), currentUser.requireId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza nome, email, role e status de um usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado"),
            @ApiResponse(responseCode = "409", description = "Email em uso / último admin / auto-rebaixamento")
    })
    public UserDetailView update(@PathVariable UUID id,
                                 @Valid @RequestBody UserEditForm form) {
        UUID adminId = currentUser.requireId();
        service.update(id, adminId, form);
        return service.detail(id, adminId);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ativa, desativa ou coloca em aprovação pendente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status alterado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado"),
            @ApiResponse(responseCode = "409", description = "Auto-desativação / último admin")
    })
    public UserDetailView changeStatus(@PathVariable UUID id,
                                       @RequestParam("status") UserStatus status) {
        UUID adminId = currentUser.requireId();
        service.changeStatus(id, adminId, status);
        return service.detail(id, adminId);
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reseta a senha e retorna uma senha temporária de uso único")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Senha resetada"),
            @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    public TempPasswordResponse resetPassword(@PathVariable UUID id) {
        return new TempPasswordResponse(service.resetPassword(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um usuário e todos os seus dados")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "404", description = "Não encontrado"),
            @ApiResponse(responseCode = "409", description = "Auto-exclusão / último admin")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id, currentUser.requireId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
