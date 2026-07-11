package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
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
import com.rastroos.domain.service.ReportService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.CategoryBreakdownDto;
import com.rastroos.web.dto.MonthSummaryDto;
import com.rastroos.web.dto.ReportsModel;

/**
 * Renderiza /app/reports com os agregados do mês selecionado. O parâmetro
 * {@code ym=YYYY-MM} é opcional (default = mês corrente). O JSON dos gráficos
 * é serializado aqui e injetado como {@code <script type="application/json">}.
 */
@Controller
@RequestMapping("/app/reports")
@PreAuthorize("isAuthenticated()")
public class ReportController {

    private final CurrentUser currentUser;
    private final ReportService reports;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ReportController(CurrentUser currentUser,
                            ReportService reports,
                            Clock clock,
                            ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.reports = reports;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String reports(@RequestParam(value = "ym", required = false) String ym,
                          Model model) {
        YearMonth period = parseOrCurrent(ym);
        ReportsModel data = reports.load(currentUser.requireId(), period);

        model.addAttribute("activeNav", "reports");
        model.addAttribute("period", period);
        model.addAttribute("periodLabel", periodLabel(period));
        model.addAttribute("data", data);
        model.addAttribute("chartDataJson", buildChartJson(data));
        return "app/reports";
    }

    /**
     * Serializa só o que o JS dos gráficos precisa: fatias (cor, valor) dos
     * donuts e a série fixo/variável dos 6 meses.
     */
    private String buildChartJson(ReportsModel data) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "byCategory", slices(data.byCategory()),
                    "byAccount", slices(data.byAccount()),
                    "fixedVar", Map.of(
                            "labels", data.trailing6().stream().map(MonthSummaryDto::label).toList(),
                            "series", List.of(
                                    Map.of("color", "#6366f1", "values",
                                            data.trailing6().stream()
                                                    .map(m -> m.fixed().doubleValue()).toList()),
                                    Map.of("color", "#fb7185", "values",
                                            data.trailing6().stream()
                                                    .map(m -> m.oneTime().doubleValue()).toList())
                            ))
            ));
        } catch (JsonProcessingException e) {
            return "{\"byCategory\":[],\"byAccount\":[],\"fixedVar\":{\"labels\":[],\"series\":[]}}";
        }
    }

    private List<Map<String, Object>> slices(List<CategoryBreakdownDto> items) {
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (CategoryBreakdownDto c : items) {
            out.add(Map.of(
                    "color", c.colorHex() == null ? "#6366f1" : c.colorHex(),
                    "value", c.amount().doubleValue()));
        }
        return out;
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
