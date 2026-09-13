package com.rastroos.web.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rastroos.domain.service.AiProviderSetting;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.form.AiProviderForm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Escolha do motor de IA da instalação — <strong>exclusiva de
 * administradores</strong>: o caminho {@code /api/admin/**} já exige
 * {@code ROLE_ADMIN} no {@code SecurityConfig}, e o {@code @PreAuthorize}
 * repete a exigência aqui para que a regra não dependa de uma linha de
 * configuração distante (§3.1).
 *
 * <p>Não trafega credencial: o corpo tem só o identificador do fornecedor. As
 * chaves continuam no ambiente, uma por motor.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · IA", description = "Motor de IA ativo na instalação")
public class AiSettingsRestController {

    private final AiProviderSetting setting;
    private final CurrentUser currentUser;
    private final AuditLogger audit;

    public AiSettingsRestController(AiProviderSetting setting, CurrentUser currentUser,
                                    AuditLogger audit) {
        this.setting = setting;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/provider")
    @Operation(summary = "Motor de IA ativo e quais estão disponíveis")
    public Map<String, Object> current() {
        return state();
    }

    @PostMapping("/provider")
    @Operation(summary = "Troca o motor de IA da instalação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Motor alterado"),
        @ApiResponse(responseCode = "400", description = "Fornecedor desconhecido, sem chave, ou travado pelo ambiente"),
        @ApiResponse(responseCode = "403", description = "Não é administrador")
    })
    public ResponseEntity<Map<String, Object>> change(@Valid @RequestBody AiProviderForm form,
                                                      HttpServletRequest request) {
        try {
            setting.set(form.getProvider(), currentUser.requireId());
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = state();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
        // Troca de motor muda para onde vão os dados financeiros do dossiê:
        // é mudança de configuração sensível e entra no audit log (§3.1).
        audit.record(currentUser.requireId(), "AI_PROVIDER_CHANGED", "app_settings",
                AiProviderSetting.KEY, request, "{\"provider\":\"" + setting.current() + "\"}");
        return ResponseEntity.ok(state());
    }

    private Map<String, Object> state() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", setting.current());
        out.put("locked", setting.locked());
        Map<String, Boolean> configured = new LinkedHashMap<>();
        for (String id : setting.known()) {
            configured.put(id, setting.configured(id));
        }
        out.put("providers", configured);
        return out;
    }
}
