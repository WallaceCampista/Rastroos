package com.rastroos.domain.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

/**
 * Dias úteis bancários no Brasil — o calendário que manda no "recebo no 5º dia
 * útil".
 *
 * <p>Considera feriado o que fecha banco em todo o país: os feriados nacionais
 * fixos, a Sexta-feira Santa, a segunda e a terça de Carnaval e Corpus Christi.
 * A quarta-feira de cinzas <b>é</b> dia útil (banco abre à tarde).
 * O Dia da Consciência Negra (20/11) entra a partir de 2024, quando virou
 * feriado nacional pela Lei 14.759/2023.
 *
 * <p><b>Limite conhecido:</b> feriados estaduais e municipais não são
 * considerados — não há como saber a cidade do usuário. Num mês em que o
 * feriado local empurre o pagamento, a data do lançamento daquele mês pode ser
 * corrigida na edição da receita.
 */
public final class BusinessDayCalendar {

    /** Nenhum mês tem mais que isto de dias úteis; serve de teto para o cadastro. */
    public static final int MAX_BUSINESS_DAYS = 23;

    private BusinessDayCalendar() {
    }

    /** {@code true} se não é sábado, domingo nem feriado bancário nacional. */
    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays(date.getYear()).contains(date);
    }

    /**
     * O n-ésimo dia útil do mês. Pedir mais dias úteis do que o mês tem devolve
     * o último — quem recebe "no 22º dia útil" não deixa de receber num mês
     * curto, recebe no fim dele.
     *
     * @param nth 1 = primeiro dia útil; valores &lt; 1 são tratados como 1
     */
    public static LocalDate nthBusinessDay(YearMonth ym, int nth) {
        int target = Math.max(1, nth);
        int count = 0;
        LocalDate last = null;
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            if (!isBusinessDay(date)) {
                continue;
            }
            count++;
            last = date;
            if (count == target) {
                return date;
            }
        }
        // Mês inteiro varrido sem atingir o alvo: cai no último dia útil.
        return last != null ? last : ym.atEndOfMonth();
    }

    /** Quantos dias úteis o mês tem. */
    public static int businessDaysIn(YearMonth ym) {
        int count = 0;
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            if (isBusinessDay(ym.atDay(day))) {
                count++;
            }
        }
        return count;
    }

    /** Em que dia útil do mês esta data cai; 0 se ela não for dia útil. */
    public static int businessDayIndexOf(LocalDate date) {
        if (!isBusinessDay(date)) {
            return 0;
        }
        YearMonth ym = YearMonth.from(date);
        int count = 0;
        for (int day = 1; day <= date.getDayOfMonth(); day++) {
            if (isBusinessDay(ym.atDay(day))) {
                count++;
            }
        }
        return count;
    }

    /** Feriados bancários nacionais do ano. */
    public static Set<LocalDate> holidays(int year) {
        Set<LocalDate> days = new HashSet<>(16);
        days.add(LocalDate.of(year, 1, 1));    // Confraternização Universal
        days.add(LocalDate.of(year, 4, 21));   // Tiradentes
        days.add(LocalDate.of(year, 5, 1));    // Dia do Trabalho
        days.add(LocalDate.of(year, 9, 7));    // Independência
        days.add(LocalDate.of(year, 10, 12));  // Nossa Senhora Aparecida
        days.add(LocalDate.of(year, 11, 2));   // Finados
        days.add(LocalDate.of(year, 11, 15));  // Proclamação da República
        days.add(LocalDate.of(year, 12, 25));  // Natal
        if (year >= 2024) {
            days.add(LocalDate.of(year, 11, 20)); // Consciência Negra (Lei 14.759/2023)
        }

        LocalDate easter = easterSunday(year);
        days.add(easter.minusDays(48));  // segunda de Carnaval
        days.add(easter.minusDays(47));  // terça de Carnaval
        days.add(easter.minusDays(2));   // Sexta-feira Santa
        days.add(easter.plusDays(60));   // Corpus Christi
        return days;
    }

    /**
     * Domingo de Páscoa pelo algoritmo gregoriano anônimo (Meeus/Jones/Butcher)
     * — é dele que saem Carnaval, Sexta-feira Santa e Corpus Christi.
     */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
