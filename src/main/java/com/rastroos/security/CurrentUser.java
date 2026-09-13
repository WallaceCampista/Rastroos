package com.rastroos.security;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.rastroos.domain.repository.UserRepository;
import com.rastroos.domain.service.ChatScope;

import jakarta.servlet.http.HttpSession;

/**
 * Acesso curto ao usuário autenticado a partir do contexto. Serviços que
 * filtram por {@code userId} devem chamar {@link #requireId()} para falhar
 * cedo se o contexto estiver vazio.
 */
@Component
public class CurrentUser {

    /** Chave do cache por requisição do acesso à IA. */
    private static final String AI_ACCESS_ATTR = "rastroos.aiAccess";

    /**
     * ObjectProvider: em fatias {@code @WebMvcTest} o repositório não existe, e
     * o {@code CurrentUser} ainda precisa ser construtível.
     */
    private final ObjectProvider<UserRepository> users;

    public CurrentUser(ObjectProvider<UserRepository> users) {
        this.users = users;
    }

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

    /**
     * {@code true} se a conta autenticada pode usar o Alfredo.
     *
     * <p>Vale a flag de <em>quem está logado</em>, não a do dono dos dados: um
     * acessor sem IA continua sem IA mesmo operando a conta de alguém que tem.
     *
     * <p>É o gate único da funcionalidade — usado tanto nos
     * {@code @PreAuthorize} das rotas de IA quanto para esconder o orbe e o
     * item de menu. Esconder sem barrar no servidor deixaria as rotas abertas
     * a quem digitasse a URL.
     */
    public boolean hasAiAccess() {
        Optional<CustomUserDetails> principal = get();
        if (principal.isEmpty()) {
            return false;
        }
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getAttribute(AI_ACCESS_ATTR, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof Boolean b) {
                return b;
            }
        }

        // Lê do banco, e não do principal: retirar o acesso precisa valer na
        // hora, não só no próximo login de quem perdeu. O cache por requisição
        // mantém isso em uma consulta por página, mesmo com o interceptor e o
        // @PreAuthorize perguntando na mesma requisição.
        UserRepository repository = users.getIfAvailable();
        boolean allowed = repository == null
                ? principal.get().isAiEnabled()
                : repository.findById(principal.get().getId())
                        .map(com.rastroos.domain.entity.User::isAiEnabled)
                        .orElse(false);

        if (attrs != null) {
            attrs.setAttribute(AI_ACCESS_ATTR, allowed, RequestAttributes.SCOPE_REQUEST);
        }
        return allowed;
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
