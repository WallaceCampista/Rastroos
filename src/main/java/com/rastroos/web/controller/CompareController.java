package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.domain.service.CompareService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.CompareModel;
import com.rastroos.web.dto.MonthSummaryDto;

/**
 * Renderiza /app/compare com os últimos 6 meses até o mês selecionado.
 * O parâmetro {@code ym=YYYY-MM} é opcional (default = mês corrente).
 */
@Controller
@RequestMapping("/app/compare")
@PreAuthorize("isAuthenticated()")
public class CompareController {

    private final CurrentUser currentUser;
    private final CompareService compare;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public CompareController(CurrentUser currentUser,
                             CompareService compare,
                             Clock clock,
                             ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.compare = compare;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String compare(@RequestParam(value = "ym", required = false) String ym,
                          Model model) {
        YearMonth period = parseOrCurrent(ym);
        CompareModel data = compare.load(currentUser.requireId(), period);

        model.addAttribute("activeNav", "compare");
        model.addAttribute("period", period);
        model.addAttribute("periodLabel", periodLabel(period));
        model.addAttribute("data", data);
        model.addAttribute("chartDataJson", buildChartJson(data));
        return "app/compare";
    }

    /**
     * Serializa a série multilinha do comparativo: recebido, gasto, saldo e
     * aporte para os 6 meses.
     */
    private String buildChartJson(CompareModel data) {
        List<MonthSummaryDto> months = data.months();
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "labels", months.stream().map(MonthSummaryDto::label).toList(),
                    "series", List.of(
                            Map.of("color", "#22c55e", "values",
                                    months.stream().map(m -> m.received().doubleValue()).toList()),
                            Map.of("color", "#ef4444", "values",
                                    months.stream().map(m -> m.spent().doubleValue()).toList()),
                            Map.of("color", "#6366f1", "values",
                                    months.stream().map(m -> m.net().doubleValue()).toList()),
                            Map.of("color", "#22d3ee", "values",
                                    months.stream().map(m -> m.invested().doubleValue()).toList())
                    )));
        } catch (JsonProcessingException e) {
            return "{\"labels\":[],\"series\":[]}";
        }
    }

    private String periodLabel(YearMonth ym) {
        Locale locale = LocaleContextHolder.getLocale();
        String month = ym.getMonth().getDisplayName(TextStyle.FULL, locale);
        if (!month.isEmpty()) {
            month = Character.toUpperCase(month.charAt(0)) + month.substring(1);
        }
        return month + " " + ym.getYear();
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
