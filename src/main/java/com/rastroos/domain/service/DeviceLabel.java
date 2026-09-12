package com.rastroos.domain.service;

/**
 * Reduz um {@code User-Agent} bruto ao nome do sistema operacional.
 *
 * <p>A coluna "Dispositivo" do histórico de login mostrava a string inteira
 * ({@code Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/…}), que
 * estoura a largura da tabela sem dizer nada de útil. Para o admin o que
 * importa é "de que máquina veio", então basta o SO.
 */
public final class DeviceLabel {

    private static final String UNKNOWN = "—";

    private DeviceLabel() {
    }

    /**
     * @param userAgent cabeçalho {@code User-Agent} da sessão (pode ser nulo)
     * @return o SO em forma curta ("Mac OS", "Windows", "Android"…) ou "—"
     */
    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase();

        // iOS antes de Mac OS: o UA do iPhone/iPad também cita "Mac OS X".
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("mac os") || ua.contains("macintosh")) {
            return "Mac OS";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("linux") || ua.contains("x11")) {
            return "Linux";
        }
        return UNKNOWN;
    }
}
