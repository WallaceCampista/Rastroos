package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * O calendário que decide a data do salário. Um erro aqui move dinheiro de mês,
 * então os casos abaixo são datas reais conferidas uma a uma.
 */
class BusinessDayCalendarTest {

    @ParameterizedTest(name = "Páscoa de {0} é {1}")
    @CsvSource({
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28"
    })
    void pascoaPeloAlgoritmoGregoriano(int year, LocalDate expected) {
        assertThat(BusinessDayCalendar.easterSunday(year)).isEqualTo(expected);
    }

    @Test
    void feriadoNacionalEmpurraODiaUtil() {
        // 07/09/2026 (Independência) cai numa segunda: o 5º dia útil escorrega
        // do dia 7 para o dia 8. É exatamente o caso que motiva a regra.
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 9), 5))
                .isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void fimDeSemanaEmpurraODiaUtil() {
        // 01/05/2026 é sexta-feira e feriado; 02 e 03 são fim de semana.
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 5), 1))
                .isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 5), 5))
                .isEqualTo(LocalDate.of(2026, 5, 8));
    }

    @Test
    void primeiroDiaUtilDeJaneiroPulaOAnoNovo() {
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 1), 1))
                .isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void carnavalNaoEDiaUtilMasQuartaDeCinzasE() {
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2026, 2, 16))).isFalse();
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2026, 2, 17))).isFalse();
        // Banco abre à tarde na quarta de cinzas — conta como dia útil.
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2026, 2, 18))).isTrue();
    }

    @Test
    void sextaSantaECorpusChristiNaoSaoDiasUteis() {
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2026, 4, 3))).isFalse();
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2026, 6, 4))).isFalse();
    }

    @Test
    void conscienciaNegraSoEFeriadoDe2024EmDiante() {
        // 20/11/2023 (segunda) ainda era dia útil nacional; a Lei 14.759/2023
        // só valeu a partir de 2024.
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2023, 11, 20))).isTrue();
        assertThat(BusinessDayCalendar.isBusinessDay(LocalDate.of(2024, 11, 20))).isFalse();
    }

    @Test
    void fevereiroComCarnavalTemMenosDiasUteis() {
        assertThat(BusinessDayCalendar.businessDaysIn(YearMonth.of(2026, 2))).isEqualTo(18);
        assertThat(BusinessDayCalendar.businessDaysIn(YearMonth.of(2026, 7))).isEqualTo(23);
    }

    @Test
    void pedirMaisDiasUteisDoQueOMesTemCaiNoUltimo() {
        // Novembro/2026 tem 19 dias úteis; quem recebe no 22º recebe no fim.
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 11), 22))
                .isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void diaUtilZeroOuNegativoViraOPrimeiro() {
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 9), 0))
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 9), -3))
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void indiceDoDiaUtilCasaComABuscaInversa() {
        LocalDate quinto = BusinessDayCalendar.nthBusinessDay(YearMonth.of(2026, 9), 5);
        assertThat(BusinessDayCalendar.businessDayIndexOf(quinto)).isEqualTo(5);
        // Sábado não é dia útil nenhum.
        assertThat(BusinessDayCalendar.businessDayIndexOf(LocalDate.of(2026, 9, 5))).isZero();
    }
}
