package com.rastroos.web.interceptor;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.rastroos.domain.service.PeriodChipsService;
import com.rastroos.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Injeta {@code monthChips} (12 meses do ano vigente, com status) sempre que o
 * controller expõe um {@code period} ({@link YearMonth}) no modelo — alimentando
 * o seletor de período da topbar sem tocar em cada controller.
 */
@Component
public class TopbarChipsInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;
    private final PeriodChipsService periodChips;

    public TopbarChipsInterceptor(CurrentUser currentUser, PeriodChipsService periodChips) {
        this.currentUser = currentUser;
        this.periodChips = periodChips;
    }

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

        final UUID userId;
        try {
            userId = currentUser.requireEffectiveId();
        } catch (RuntimeException ignored) {
            // Sem usuário efetivo (landing/auth) → nada de chips/streak.
            return;
        }

        // O streak (meses no/fora do controle) é por-usuário e independe do período,
        // então aparece na sidebar de TODAS as telas do app (Alfredo, Acessores,
        // Suporte inclusive), não só nas que expõem um `period`.
        try {
            PeriodChipsService.StreakView streak = periodChips.streak(userId);
            modelAndView.addObject("streakInControl", streak.inControl());
            modelAndView.addObject("streakOutControl", streak.outControl());
        } catch (RuntimeException ignored) {
            // Nunca quebra a página por causa do streak.
        }

        // Os chips de mês da topbar dependem de um período no modelo.
        if (modelAndView.getModel().get("period") instanceof YearMonth period) {
            try {
                modelAndView.addObject("monthChips", periodChips.yearChips(userId, period));
            } catch (RuntimeException ignored) {
                // Sem chips → o fragmento cai no seletor simples.
            }
        }
    }
}
