package com.rastroos.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.AppSetting;
import com.rastroos.domain.repository.AppSettingRepository;

/**
 * Qual motor de IA está ativo na instalação — decisão do administrador,
 * guardada em {@code app_settings} para sobreviver a um restart.
 *
 * <p><strong>A chave não passa por aqui.</strong> Cada fornecedor tem a sua no
 * ambiente ({@code ai.keys.openai}, {@code ai.keys.gemini}); alternar escolhe
 * qual credencial e qual API entram em uso, sem nunca gravar segredo no banco.
 *
 * <p>Com {@code ai.provider-locked=true} (o caso de dev) não há escolha: vale
 * sempre {@code ai.provider} e qualquer tentativa de trocar é recusada.
 */
@Service
public class AiProviderSetting {

    /** Chave em {@code app_settings}. */
    public static final String KEY = "ai.provider";

    private static final Logger log = LoggerFactory.getLogger(AiProviderSetting.class);

    private final AppSettingRepository settings;
    private final AiProperties props;
    private final List<String> known;

    /**
     * Leitura em cache: o valor é consultado a cada chamada de IA e muda só
     * quando um administrador troca. {@code null} = ainda não lido do banco.
     */
    private volatile String cached;

    public AiProviderSetting(AppSettingRepository settings, AiProperties props,
                             List<AiProvider> providers) {
        this.settings = settings;
        this.props = props;
        this.known = providers.stream().map(AiProvider::id).toList();
    }

    /** Fornecedores implementados, na ordem em que a UI deve mostrá-los. */
    public List<String> known() {
        return known;
    }

    /** {@code true} quando o ambiente fixa o motor e a troca não é permitida. */
    public boolean locked() {
        return props.isProviderLocked();
    }

    /** {@code true} se aquele fornecedor tem credencial configurada. */
    public boolean configured(String providerId) {
        String key = props.apiKeyFor(providerId);
        return key != null && !key.isBlank();
    }

    /** O motor ativo agora. */
    public String current() {
        if (locked()) {
            return normalize(props.getProvider());
        }
        String value = cached;
        if (value == null) {
            value = load();
            cached = value;
        }
        return value;
    }

    /**
     * Troca o motor ativo.
     *
     * @throws IllegalArgumentException fornecedor desconhecido, sem credencial,
     *                                  ou ambiente com o motor travado
     */
    @Transactional
    public void set(String providerId, UUID actorId) {
        if (locked()) {
            throw new IllegalArgumentException("ai.providerLocked");
        }
        String wanted = normalize(providerId);
        if (!known.contains(wanted)) {
            throw new IllegalArgumentException("ai.providerUnknown");
        }
        // Trocar para um motor sem chave desligaria a IA inteira em silêncio.
        if (!configured(wanted)) {
            throw new IllegalArgumentException("ai.providerNotConfigured");
        }

        AppSetting row = settings.findById(KEY)
                .orElseGet(() -> new AppSetting(KEY, wanted, actorId));
        row.setValue(wanted);
        row.setUpdatedBy(actorId);
        settings.save(row);
        cached = wanted;
        log.info("Motor de IA alterado para '{}'", wanted);
    }

    private String load() {
        Optional<AppSetting> row = settings.findById(KEY);
        String stored = row.map(AppSetting::getValue).map(this::normalize).orElse(null);
        if (stored != null && known.contains(stored)) {
            return stored;
        }
        // Sem escolha gravada (ou gravada num fornecedor que sumiu do código):
        // vale o do ambiente.
        return normalize(props.getProvider());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
