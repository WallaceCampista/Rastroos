package com.rastroos.web.support;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Resolve o período (mês/ano) das telas do app a partir do parâmetro
 * {@code ym=YYYY-MM}, lembrando a última escolha <b>pela sessão</b>.
 *
 * <p>Sem isso cada controller caía em {@code YearMonth.now()} quando a URL não
 * trazia {@code ym} — e como os links da sidebar não carregam o parâmetro,
 * trocar de tela jogava o usuário de volta para o mês corrente. Guardando a
 * escolha na sessão, o mês corrente só vale como padrão no primeiro acesso:
 * a sessão nasce no login e morre no logout, então o comportamento é
 * "abre no mês atual ao entrar, depois respeita o que o usuário escolheu".
 */
@Component
public class PeriodResolver {

    static final String SESSION_KEY = "rastroos.period";

    private final Clock clock;
    private final HttpServletRequest request;

    public PeriodResolver(Clock clock, HttpServletRequest request) {
        this.clock = clock;
        this.request = request;
    }

    /**
     * @param ym período pedido na query string ({@code YYYY-MM}), ou {@code null}
     * @return o período pedido (memorizado na sessão), o último memorizado, ou o mês corrente
     */
    public YearMonth resolve(String ym) {
        YearMonth requested = parse(ym);
        if (requested != null) {
            remember(requested);
            return requested;
        }
        YearMonth remembered = parse(readSession());
        return remembered != null ? remembered : YearMonth.now(clock);
    }

    /** Esquece a escolha — o próximo acesso volta ao mês corrente. */
    public void forget() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }

    private void remember(YearMonth period) {
        request.getSession().setAttribute(SESSION_KEY, period.toString());
    }

    private String readSession() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return session.getAttribute(SESSION_KEY) instanceof String s ? s : null;
    }

    private static YearMonth parse(String ym) {
        if (ym == null || ym.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(ym);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
