package com.rastroos.web.form;

import java.util.UUID;

import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Form de edição de usuário pelo admin. Não altera senha (ação separada de
 * reset). Nome, email, role e status são editáveis; o service impede rebaixar
 * ou desativar o último admin ativo.
 */
@Schema(description = "Dados para o admin editar um usuário (sem alterar senha)")
public class UserEditForm {

    @NotBlank
    @Size(max = 120)
    @Schema(description = "Nome completo", example = "Maria Silva")
    private String name;

    @NotBlank
    @Email
    @Size(max = 180)
    @Schema(description = "Email (único, case-insensitive)", example = "maria@example.com")
    private String email;

    @NotNull
    @Schema(description = "Papel do usuário", example = "USER")
    private UserRole role;

    @NotNull
    @Schema(description = "Status da conta", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Usuário-alvo cujos dados o acessor operará "
            + "(obrigatório e válido apenas quando role = ACESSOR)")
    private UUID accessesUserId;

    public UserEditForm() {
    }

    public UUID getAccessesUserId() { return accessesUserId; }
    public void setAccessesUserId(UUID accessesUserId) { this.accessesUserId = accessesUserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
