package com.rastroos.security;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.rastroos.domain.service.ChatScope;

import jakarta.servlet.http.HttpSession;

/**
 * Acesso curto ao usuário autenticado a partir do contexto. Serviços que
 * filtram por {@code userId} devem chamar {@link #requireId()} para falhar
 * cedo se o contexto estiver vazio.
 */
@Component
public class CurrentUser {

    public Optional<CustomUserDetails> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        Object principal = auth.getPrincipal();
        return principal instanceof CustomUserDetails details
                ? Optional.of(details)
                : Optional.empty();
    }

    public Optional<UUID> id() {
        return get().map(CustomUserDetails::getId);
    }

    public UUID requireId() {
        return id().orElseThrow(() ->
                new IllegalStateException("No authenticated user in SecurityContext"));
    }

    /** {@code true} se o usuário autenticado tem a authority {@code ROLE_ADMIN}. */
    public boolean isAdmin() {
        return get()
                .map(u -> u.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
                .orElse(false);
    }

    /** {@code true} se a conta autenticada é um ACESSOR (opera dados de outro usuário). */
    public boolean isAccessor() {
        return get().map(CustomUserDetails::isAccessor).orElse(false);
    }

    /**
     * Id do <em>dono dos dados</em>: para um ACESSOR é o usuário-alvo
     * ({@code accessesUserId}); para os demais, o próprio id. É este o id que
     * os controllers/serviços de dados financeiros devem usar para filtrar —
     * derivado sempre do principal autenticado, nunca de parâmetro do request.
     */
    public Optional<UUID> effectiveUserId() {
        // Admin em modo "ver como": override guardado na sessão (admin-only, validado no endpoint).
        Optional<UUID> viewAs = viewAsUserId();
        if (viewAs.isPresent()) return viewAs;
        return get().map(u -> u.isAccessor() ? u.getAccessesUserId() : u.getId());
    }

    /** Chave da sessão para o "ver como" do admin. */
    public static final String VIEW_AS_SESSION_KEY = "rastroos.viewAsUserId";

    /**
     * Id do usuário que o admin escolheu "ver" (sessão). Vazio se não é admin ou
     * não há seleção. É a base do modo "ver como": afeta {@link #effectiveUserId()}.
     */
    public Optional<UUID> viewAsUserId() {
        if (!isAdmin()) return Optional.empty();
        HttpSession session = currentSession(false);
        if (session == null) return Optional.empty();
        Object v = session.getAttribute(VIEW_AS_SESSION_KEY);
        return v instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
    }

    private HttpSession currentSession(boolean create) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getSession(create);
        }
        return null;
    }

    public UUID requireEffectiveId() {
        return effectiveUserId().orElseThrow(() ->
                new IllegalStateException("No effective data owner in SecurityContext"));
    }

    /** Contas ACESSOR não podem excluir nada. */
    public boolean canDelete() {
        return !isAccessor();
    }

    /** {@code true} quando o acessor logado está com valores mascarados pelo titular. */
    public boolean isMaskActive() {
        return get().map(u -> u.isAccessor() && u.isValuesMasked()).orElse(false);
    }

    /**
     * Escopo da conversa com o Alfredo: a conversa fica na conta autenticada e
     * os números vêm do dono dos dados.
     *
     * <p>Com valores mascarados pelo titular, o escopo sai <em>sem</em> dono de
     * dados — o Alfredo então não recebe número nenhum no contexto, em vez de
     * receber e ser instruído a não usar. Um acessor mascarado não pode extrair
     * valores fazendo a pergunta certa.
     */
    public ChatScope chatScope(YearMonth period) {
        UUID owner = requireId();
        return isMaskActive()
                ? ChatScope.masked(owner, period)
                : new ChatScope(owner, requireEffectiveId(), period);
    }

    /** Nome do usuário-alvo (para o banner), quando a conta é um ACESSOR. */
    public Optional<String> accessTargetName() {
        return get().map(CustomUserDetails::getTargetName);
    }
}
