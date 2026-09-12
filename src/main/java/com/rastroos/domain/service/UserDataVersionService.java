package com.rastroos.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.rastroos.domain.entity.AiUserState;
import com.rastroos.domain.repository.AiUserStateRepository;

/**
 * Mantém o contador "os dados deste usuário mudaram" e a janela de debounce
 * usada pelo pré-aquecimento dos resumos.
 *
 * <p>O incremento é um UPDATE atômico no banco, não um read-modify-write: duas
 * escritas simultâneas do mesmo usuário não podem perder versão, senão uma
 * alteração ficaria para sempre fora do resumo.
 *
 * <p>Roda depois do commit da escrita que o originou
 * ({@link TransactionPhase#AFTER_COMMIT}) e em transação própria: marcar o
 * estado nunca deve influenciar — nem ser desfeito por — a transação de
 * negócio.
 */
@Service
public class UserDataVersionService {

    private static final Logger log = LoggerFactory.getLogger(UserDataVersionService.class);

    private final AiUserStateRepository states;
    private final Clock clock;

    public UserDataVersionService(AiUserStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    /**
     * Consome o evento das telas de escrita. {@code fallbackExecution} cobre
     * chamadas feitas fora de transação (testes, jobs administrativos).
     *
     * <p>A transação é declarada <strong>aqui</strong>, e não delegada a
     * {@link #markChanged}: um método chamando outro do mesmo bean não passa
     * pelo proxy do Spring, e o UPDATE do {@code bump} precisa de transação
     * ativa para rodar.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDataChanged(UserDataChangedEvent event) {
        bumpQuietly(event.userId());
    }

    /**
     * Sobe a versão do usuário e abre a janela de debounce, criando o estado na
     * primeira vez. Ponto de entrada para quem chama de fora (jobs, testes).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChanged(UUID userId) {
        bumpQuietly(userId);
    }

    /**
     * Nunca propaga erro: falhar aqui só adiaria a regeração do resumo para o
     * próximo page load — não pode derrubar o lançamento que a pessoa acabou de
     * salvar.
     */
    private void bumpQuietly(UUID userId) {
        try {
            if (states.bump(userId, Instant.now(clock)) == 0) {
                insertInitial(userId);
            }
        } catch (RuntimeException e) {
            log.warn("Não consegui marcar mudança de dados do usuário para a IA: {}", e.toString());
        }
    }

    /**
     * Cria o estado já marcado como sujo. Uma corrida entre duas primeiras
     * escritas cai na chave primária: nesse caso basta incrementar a linha que
     * o concorrente criou.
     */
    private void insertInitial(UUID userId) {
        AiUserState state = new AiUserState();
        state.setUserId(userId);
        state.setDataVersion(1L);
        state.setWarmedVersion(-1L);
        state.setIndexedVersion(-1L);
        state.setDirtySince(Instant.now(clock));
        try {
            states.save(state);
        } catch (DataIntegrityViolationException e) {
            states.bump(userId, Instant.now(clock));
        }
    }

    /**
     * Reserva o usuário para aquecimento: limpa a marca de sujeira <em>antes</em>
     * de gerar, para que uma segunda varredura (ou outra instância) não refaça o
     * mesmo trabalho. Se a geração falhar, {@link #markWarmFailed} remarca.
     */
    @Transactional
    public void claimForWarmup(UUID userId) {
        states.findByUserId(userId).ifPresent(state -> {
            state.setDirtySince(null);
            states.save(state);
        });
    }

    /** Aquecimento concluído até {@code version}. */
    @Transactional
    public void markWarmed(UUID userId, long version) {
        states.findByUserId(userId).ifPresent(state -> {
            state.setWarmedVersion(version);
            state.setIndexedVersion(version);
            state.setLastWarmAt(Instant.now(clock));
            state.setLastWarmError(null);
            // Escreveu de novo durante o aquecimento? Segue sujo para a próxima volta.
            if (state.getDataVersion() > version) {
                state.setDirtySince(Instant.now(clock));
            }
            states.save(state);
        });
    }

    /**
     * Aquecimento falhou: reagenda para daqui a {@code backoffSeconds}.
     *
     * <p>A marca de sujeira é colocada <em>no futuro</em> de propósito. O
     * varredor só pega quem tem {@code dirtySince} vencido, então empurrá-la
     * para frente é o que transforma um problema permanente (conta sem
     * crédito, chave revogada) em uma tentativa a cada poucos minutos em vez
     * de uma a cada varredura, para sempre.
     */
    @Transactional
    public void markWarmFailed(UUID userId, String reason, int backoffSeconds) {
        states.findByUserId(userId).ifPresent(state -> {
            state.setDirtySince(Instant.now(clock).plusSeconds(Math.max(0, backoffSeconds)));
            state.setLastWarmError(reason == null ? null
                    : reason.substring(0, Math.min(reason.length(), 300)));
            states.save(state);
        });
    }

    @Transactional(readOnly = true)
    public long currentVersion(UUID userId) {
        return states.findByUserId(userId).map(AiUserState::getDataVersion).orElse(0L);
    }

    @Transactional(readOnly = true)
    public Optional<AiUserState> state(UUID userId) {
        return states.findByUserId(userId);
    }
}
