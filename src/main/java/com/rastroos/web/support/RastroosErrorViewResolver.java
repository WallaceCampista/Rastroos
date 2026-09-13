package com.rastroos.web.support;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Página de erro própria do Rastroo$, no lugar da "Whitelabel Error Page".
 *
 * <p>Só é consultado para respostas <b>HTML</b> ({@code BasicErrorController#errorHtml});
 * o JSON de erro das rotas {@code /api/**} continua exatamente como era.
 *
 * <p><strong>O modelo é montado do zero, de propósito.</strong> O mapa que chega
 * traz {@code message}, {@code trace} e {@code exception} quando o perfil dev
 * libera — e um template que recebe isso um dia acaba mostrando. Aqui a página
 * só conhece o status, o endereço e as chaves de texto: não há como vazar
 * detalhe interno (§3.2), em perfil nenhum.
 *
 * <p>Os textos moram em {@code messages*.properties} (§8): este resolvedor só
 * escolhe as chaves.
 */
@Component
public class RastroosErrorViewResolver implements ErrorViewResolver {

    static final String VIEW = "error/page";

    static final String HOME = "/app/dashboard";
    static final String LOGIN = "/?openLogin=login";
    static final String SUPPORT = "/app/support";

    /** Rotas do Alfredo: um 403 nelas quase sempre é conta sem IA liberada. */
    private static final String[] AI_PATHS = {
        "/app/manager", "/api/v1/chats", "/api/v1/insights"
    };

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status,
                                         Map<String, Object> model) {
        String path = model.get("path") instanceof String p && !p.isBlank()
                ? p
                : request.getRequestURI();
        String key = keyFor(status.value(), path);

        Map<String, Object> view = new HashMap<>();
        view.put("status", status.value());
        view.put("path", path);
        view.put("titleKey", key + ".title");
        view.put("detailKey", key + ".detail");

        if (status.value() == 401) {
            view.put("actionKey", "error.action.login");
            view.put("actionHref", LOGIN);
        } else {
            view.put("actionKey", "error.action.home");
            view.put("actionHref", HOME);
        }
        // Erro do servidor não tem o que o usuário corrigir: a direção útil é
        // registrar o problema.
        if (status.is5xxServerError()) {
            view.put("secondaryKey", "error.action.support");
            view.put("secondaryHref", SUPPORT);
        }
        return new ModelAndView(VIEW, view, status);
    }

    /** Chave-base do texto: específica quando existe, genérica por família senão. */
    static String keyFor(int status, String path) {
        if (status == 403 && isAiPath(path)) {
            return "error.403ai";
        }
        return switch (status) {
            case 400, 401, 403, 404, 405, 413, 429, 500, 503 -> "error." + status;
            default -> status >= 500 ? "error.5xx" : "error.4xx";
        };
    }

    private static boolean isAiPath(String path) {
        if (path == null) {
            return false;
        }
        for (String prefix : AI_PATHS) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
