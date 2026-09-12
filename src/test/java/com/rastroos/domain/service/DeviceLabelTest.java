package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceLabelTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/140.0 Safari/537.36 | Mac OS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0                     | Windows",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140.0                               | Linux",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/140.0                      | Android",
        "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 Chrome/140.0                      | ChromeOS",
    })
    void reduzOUserAgentAoSistemaOperacional(String userAgent, String esperado) {
        assertThat(DeviceLabel.of(userAgent)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1",
    })
    void iosVemAntesDeMacOsPorqueOUserAgentDoIphoneTambemCitaMacOsX(String userAgent) {
        assertThat(DeviceLabel.of(userAgent)).isEqualTo("iOS");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "curl/8.7.1" })
    void semUserAgentReconhecivelMostraTraco(String userAgent) {
        assertThat(DeviceLabel.of(userAgent)).isEqualTo("—");
    }

    @Test
    void naoDependeDeCaixa() {
        assertThat(DeviceLabel.of("MOZILLA/5.0 (MACINTOSH; INTEL MAC OS X)")).isEqualTo("Mac OS");
    }
}
