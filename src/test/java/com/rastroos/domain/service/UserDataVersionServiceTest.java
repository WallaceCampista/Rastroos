package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.rastroos.domain.entity.AiUserState;
import com.rastroos.domain.repository.AiUserStateRepository;

/**
 * Contador de "os dados mudaram". É o único gatilho de consumo dos resumos:
 * sem escrita não há marca, sem marca não há regeração, sem regeração não há
 * custo.
 */
@ExtendWith(MockitoExtension.class)
class UserDataVersionServiceTest {

    @Mock private AiUserStateRepository states;

    private final Instant now = Instant.parse("2026-09-12T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID alice = UUID.randomUUID();

    private UserDataVersionService service;

    @BeforeEach
    void setUp() {
        service = new UserDataVersionService(states, clock);
    }

    @Test
    void eventoDeEscrita_incrementaAVersao() {
        when(states.bump(alice, now)).thenReturn(1);

        service.onDataChanged(new UserDataChangedEvent(alice));

        verify(states).bump(alice, now);
    }

    @Test
    void primeiraEscritaDoUsuario_criaOEstadoJaSujo() {
        when(states.bump(alice, now)).thenReturn(0);

        service.markChanged(alice);

        ArgumentCaptor<AiUserState> saved = ArgumentCaptor.forClass(AiUserState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(alice);
        assertThat(saved.getValue().getDataVersion()).isEqualTo(1L);
        assertThat(saved.getValue().getDirtySince()).isEqualTo(now);
        assertThat(saved.getValue().getWarmedVersion()).isEqualTo(-1L);
    }

    @Test
    void corridaEntreDuasPrimeirasEscritas_naoPerdeVersao() {
        when(states.bump(alice, now)).thenReturn(0, 1);
        when(states.save(any(AiUserState.class)))
                .thenThrow(new DataIntegrityViolationException("chave duplicada"));

        service.markChanged(alice);

        // Perdeu a corrida: em vez de estourar, incrementa a linha do concorrente.
        verify(states, org.mockito.Mockito.times(2)).bump(alice, now);
    }

    @Test
    void falhaAoMarcar_naoDerrubaOLancamentoQueAPessoaAcabouDeSalvar() {
        when(states.bump(any(), any())).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(() -> service.markChanged(alice)).doesNotThrowAnyException();
    }

    @Test
    void reservaParaAquecimento_limpaAMarcaAntesDeGerar() {
        AiUserState state = state(5L, null);
        state.setDirtySince(now);
        when(states.findByUserId(alice)).thenReturn(Optional.of(state));

        service.claimForWarmup(alice);

        assertThat(state.getDirtySince()).isNull();
        verify(states).save(state);
    }

    @Test
    void aquecimentoConcluido_registraAVersaoEApagaOErro() {
        AiUserState state = state(7L, "falha anterior");
        when(states.findByUserId(alice)).thenReturn(Optional.of(state));

        service.markWarmed(alice, 7L);

        assertThat(state.getWarmedVersion()).isEqualTo(7L);
        assertThat(state.getIndexedVersion()).isEqualTo(7L);
        assertThat(state.getLastWarmAt()).isEqualTo(now);
        assertThat(state.getLastWarmError()).isNull();
        assertThat(state.getDirtySince()).isNull();
    }

    @Test
    void escritaDuranteOAquecimento_mantemOUsuarioSujoParaAProximaVolta() {
        AiUserState state = state(9L, null);   // já subiu para 9 enquanto gerava a 7
        when(states.findByUserId(alice)).thenReturn(Optional.of(state));

        service.markWarmed(alice, 7L);

        assertThat(state.getWarmedVersion()).isEqualTo(7L);
        assertThat(state.getDirtySince()).isEqualTo(now);
    }

    @Test
    void falhaNoAquecimento_reagendaNoFuturo_naoParaAVarreduraSeguinte() {
        AiUserState state = state(2L, null);
        when(states.findByUserId(alice)).thenReturn(Optional.of(state));

        service.markWarmFailed(alice, "provedor sem crédito", 300);

        assertThat(state.getDirtySince()).isEqualTo(now.plusSeconds(300));
        assertThat(state.getLastWarmError()).isEqualTo("provedor sem crédito");
    }

    @Test
    void motivoLongoEhTruncadoNoTamanhoDaColuna() {
        AiUserState state = state(1L, null);
        when(states.findByUserId(alice)).thenReturn(Optional.of(state));

        service.markWarmFailed(alice, "x".repeat(500), 60);

        assertThat(state.getLastWarmError()).hasSize(300);
    }

    @Test
    void versaoCorrenteDeUsuarioSemEstado_ehZero() {
        when(states.findByUserId(eq(alice))).thenReturn(Optional.empty());

        assertThat(service.currentVersion(alice)).isZero();
    }

    private AiUserState state(long dataVersion, String error) {
        AiUserState state = new AiUserState();
        state.setUserId(alice);
        state.setDataVersion(dataVersion);
        state.setLastWarmError(error);
        return state;
    }
}
