package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.domain.service.DashboardService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.BalancePointDto;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.DashboardModel;

/**
 * Renderiza a tela /app/dashboard com os agregados do mês selecionado.
 * O parâmetro {@code ym=YYYY-MM} é opcional; default = mês corrente.
 */
@Controller
@RequestMapping("/app")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final CurrentUser currentUser;
    private final DashboardService dashboard;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public DashboardController(CurrentUser currentUser,
                               DashboardService dashboard,
                               Clock clock,
                               ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.dashboard = dashboard;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "ym", required = false) String ym,
                            Model model) {
        YearMonth period = parseOrCurrent(ym);
        DashboardModel data = dashboard.load(currentUser.requireEffectiveId(), period);

        currentUser.get().ifPresent(u -> {
            model.addAttribute("userEmail", u.getEmail());
            model.addAttribute("isAdmin",
                    u.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        });
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("period", period);
        model.addAttribute("data", data);
        // Acessor com valores mascarados: não emitir a série real (vazaria no view-source).
        model.addAttribute("chartDataJson",
                currentUser.isMaskActive() ? "{\"balance\":[],\"dailySpend\":[],\"byCategory\":[]}"
                        : buildChartJson(data));
        return "app/dashboard";
    }

    /**
     * Serializa só o que o JS dos gráficos precisa: pontos (dia, saldo) e
     * fatias (cor, valor). Mantemos fora os textos potencialmente sensíveis.
     */
    private String buildChartJson(DashboardModel data) {
        List<Map<String, Object>> balance = new ArrayList<>(data.balanceSeries().size());
        for (BalancePointDto p : data.balanceSeries()) {
            balance.add(Map.of(
                    "x", p.day(),
                    "y", p.balance().doubleValue()
            ));
        }
        List<Double> dailySpend = new ArrayList<>(data.dailySpend().size());
        for (java.math.BigDecimal v : data.dailySpend()) {
            dailySpend.add(v.doubleValue());
        }
        List<Map<String, Object>> byCategory = new ArrayList<>(data.byCategory().size());
        for (CategoryBreakdownDto c : data.byCategory()) {
            byCategory.add(Map.of(
                    "color", c.colorHex() == null ? "#6366f1" : c.colorHex(),
                    "value", c.amount().doubleValue()
            ));
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "balance", balance,
                    "dailySpend", dailySpend,
                    "byCategory", byCategory
            ));
        } catch (JsonProcessingException e) {
            return "{\"balance\":[],\"dailySpend\":[],\"byCategory\":[]}";
        }
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
