package com.rastroos.config;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.rastroos.domain.service.AiProvider;
import com.rastroos.domain.service.GeminiProvider;
import com.rastroos.domain.service.OpenAiProvider;

/**
 * Habilita a vinculação de {@link AiProperties} e {@link ExtractionProperties}
 * e liga o agendamento usado pelo pré-aquecimento dos resumos.
 *
 * <p>Os clientes HTTP são construídos dentro do próprio
 * {@code AiModelClient} a partir das propriedades, para não criar
 * {@code RestClient} algum quando a integração está desligada.
 *
 * <p>{@code @EnableScheduling} + {@code @EnableAsync} existem para a varredura
 * que regera resumos fora do ciclo da requisição — é o que mantém a IA fora do
 * caminho do page load.
 */
@Configuration
@EnableConfigurationProperties({AiProperties.class, ExtractionProperties.class})
@EnableScheduling
@EnableAsync
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * Fornecedores conhecidos. Acrescentar um motor com API própria é
     * implementar {@link AiProvider} e incluí-lo nesta lista — nenhuma outra
     * classe precisa saber que ele existe.
     */
    private static final List<AiProvider> KNOWN = List.of(
            new OpenAiProvider(),
            new GeminiProvider());

    /**
     * Todos os fornecedores como beans: o {@code AiModelClient} monta um motor
     * para cada um que tiver credencial, e o administrador alterna entre eles
     * em tempo de execução ({@link com.rastroos.domain.service.AiProviderSetting}).
     *
     * <p>A ordem desta lista é a que a UI mostra.
     */
    @Bean
    List<AiProvider> aiProviders(AiProperties props) {
        String wanted = props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase();
        if (KNOWN.stream().noneMatch(p -> p.id().equals(wanted))) {
            log.warn("ai.provider='{}' desconhecido; o padrão será '{}'. Disponíveis: {}",
                    props.getProvider(), KNOWN.get(0).id(),
                    KNOWN.stream().map(AiProvider::id).collect(Collectors.joining(", ")));
        }
        return KNOWN;
    }
}
