package com.rastroos.web.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.rastroos.web.dto.InsightScreen;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Decide, a partir do {@code activeNav} que o controller já publica, o que o
 * widget flutuante do Alfredo deve fazer na tela:
 *
 * <ul>
 *   <li>{@code alfredoWidget} — renderizar o orbe (falso só na tela do
 *       Alfredo, onde o chat já ocupa a tela inteira);</li>
 *   <li>{@code alfredoScreen} / {@code alfredoScreenLabel} — a tela tem resumo
 *       ({@link InsightScreen}); ausentes, o orbe vira só atalho de chat.</li>
 * </ul>
 *
 * <p>Fica num interceptor (e não em cada controller) porque o mapa
 * activeNav → tela-com-resumo é único: o enum é a fonte da verdade, e nem o
 * template nem o JS repetem essa lista.
 */
@Component
public class AlfredoWidgetInterceptor implements HandlerInterceptor {

    /** Tela do próprio Alfredo: o widget não aparece nela. */
    private static final String MANAGER_NAV = "manager";

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView == null) {
            return;
        }
        String viewName = modelAndView.getViewName();
        if (viewName != null && (viewName.startsWith("redirect:") || viewName.startsWith("forward:"))) {
            return;
        }

        String nav = modelAndView.getModel().get("activeNav") instanceof String s ? s : null;
        modelAndView.addObject("alfredoWidget", !MANAGER_NAV.equals(nav));
        InsightScreen.parse(nav).ifPresent(screen -> {
            modelAndView.addObject("alfredoScreen", screen.key());
            modelAndView.addObject("alfredoScreenLabel", screen.label());
        });
    }
}
