package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Chat;
import com.rastroos.domain.entity.ChatMessage;
import com.rastroos.domain.entity.enums.ChatMessageRole;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.web.dto.ChatDetailDto;
import com.rastroos.web.dto.ChatMessageDto;
import com.rastroos.web.dto.ManagerView;

/**
 * Cobre o {@link ChatService}: persistência do par pergunta/resposta,
 * isolamento por usuário e — o que mudou de mais importante — o
 * <strong>dossiê de dados reais</strong> que acompanha toda pergunta. Sem ele,
 * o modelo responderia sobre dinheiro alheio com números inventados.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatStore store;
    @Mock private AlfredoAiClient ai;
    @Mock private FinancialContextBuilder contextBuilder;
    @Mock private SemanticSearchService semantic;

    @InjectMocks private ChatService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 9);
    private final ChatScope aliceScope = ChatScope.own(alice, period);

    // ── Dossiê (a garantia de exatidão) ──────────────────────────────────

    @Test
    void todaPerguntaLevaOsDadosReaisDoDonoEOsTrechosSemelhantes() {
        UUID chatId = stubNewChat();
        when(contextBuilder.build(alice, period)).thenReturn("DADOS REAIS\n- Gasto: R$ 10,00");
        when(semantic.search(alice, "Quanto gastei?")).thenReturn(List.of());
        when(semantic.asPromptSection(List.of())).thenReturn("");
        when(ai.reply(eq(alice), eq("Quanto gastei?"), anyList(), anyString()))
                .thenReturn("Você gastou R$ 10,00.");

        service.start(aliceScope, "  Quanto gastei?  ");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).reply(eq(alice), eq("Quanto gastei?"), anyList(), context.capture());
        assertThat(context.getValue()).contains("DADOS REAIS").contains("R$ 10,00");
        assertThat(chatId).isNotNull();
    }

    @Test
    void trechosSemelhantesSaoAnexadosAoDossie() {
        stubNewChat();
        List<VectorMatch> matches = List.of(
                new VectorMatch("TRANSACTION", "1", "Gasto: Farmácia", null, 8790L, 0.9));
        when(contextBuilder.build(alice, period)).thenReturn("DADOS REAIS");
        when(semantic.search(alice, "compra da farmácia")).thenReturn(matches);
        when(semantic.asPromptSection(matches)).thenReturn("\n## Registros parecidos\n- Farmácia\n");
        when(ai.reply(eq(alice), anyString(), anyList(), anyString())).thenReturn("ok");

        service.start(aliceScope, "compra da farmácia");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).reply(eq(alice), anyString(), anyList(), context.capture());
        assertThat(context.getValue()).contains("DADOS REAIS").contains("Registros parecidos");
    }

    @Test
    void acessorComValoresMascarados_naoRecebeNenhumNumeroNoContexto() {
        stubNewChat();
        when(ai.reply(eq(alice), anyString(), anyList(), anyString())).thenReturn("ok");

        service.start(ChatScope.masked(alice, period), "Quanto sobrou?");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).reply(eq(alice), anyString(), anyList(), context.capture());
        assertThat(context.getValue())
                .contains("indisponíveis")
                .contains("ocultar os valores")
                .doesNotContain("R$");
        // Nem chega a ler o banco: sem dono de dados, não há o que montar.
        verifyNoInteractions(contextBuilder, semantic);
    }

    @Test
    void oDossieVemDoDonoDosDados_naoDeQuemPergunta() {
        stubNewChat();
        ChatScope acessor = new ChatScope(alice, bob, period);
        when(contextBuilder.build(bob, period)).thenReturn("DADOS DO TITULAR");
        when(semantic.search(bob, "Quanto gastei?")).thenReturn(List.of());
        when(semantic.asPromptSection(List.of())).thenReturn("");
        when(ai.reply(eq(alice), anyString(), anyList(), anyString())).thenReturn("ok");

        service.start(acessor, "Quanto gastei?");

        verify(contextBuilder).build(bob, period);
        verify(contextBuilder, never()).build(eq(alice), any());
    }

    // ── Persistência e título ────────────────────────────────────────────

    @Test
    void startCriaConversaPersisteParDeMensagensEDerivaTitulo() {
        stubNewChat();
        stubContext();
        when(ai.reply(eq(alice), eq("Quanto gastei?"), anyList(), anyString()))
                .thenReturn("Você gastou R$ 1.234.");

        service.start(aliceScope, "  Quanto gastei?  ");

        verify(store).create(alice, "Quanto gastei?");
        verify(store).append(any(), eq(ChatMessageRole.USER), eq("Quanto gastei?"));
        verify(store).append(any(), eq(ChatMessageRole.ASSISTANT), eq("Você gastou R$ 1.234."));
    }

    @Test
    void aPerguntaEhGravadaAntesDeChamarAIa_paraNaoSePerderSeOProvedorCair() {
        UUID chatId = stubNewChat();
        stubContext();
        when(ai.reply(eq(alice), anyString(), anyList(), anyString()))
                .thenThrow(new IllegalStateException("provedor fora"));

        assertThatThrownBy(() -> service.start(aliceScope, "importante"))
                .isInstanceOf(IllegalStateException.class);

        verify(store).append(chatId, ChatMessageRole.USER, "importante");
        verify(store, never()).append(any(), eq(ChatMessageRole.ASSISTANT), anyString());
    }

    @Test
    void startComTituloLongoTrunca() {
        stubNewChat();
        stubContext();
        when(ai.reply(eq(alice), anyString(), anyList(), anyString())).thenReturn("ok");

        service.start(aliceScope, "palavra ".repeat(30));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(store).create(eq(alice), title.capture());
        assertThat(title.getValue()).endsWith("…").hasSizeLessThanOrEqualTo(ChatService.TITLE_MAX);
    }

    @Test
    void startEmptyGravaSoASaudacaoESemChamarAIa() {
        UUID chatId = stubNewChat();

        assertThat(service.startEmpty(alice)).isEqualTo(chatId);

        verify(store).create(alice, "Nova conversa");
        verify(store).append(eq(chatId), eq(ChatMessageRole.ASSISTANT), anyString());
        verifyNoInteractions(ai, contextBuilder, semantic);
    }

    // ── Isolamento ───────────────────────────────────────────────────────

    @Test
    void sendEmConversaDeOutroUsuarioRetorna404() {
        UUID chatId = UUID.randomUUID();
        when(store.require(alice, chatId))
                .thenThrow(new ResourceNotFoundException("chat.notFound"));

        assertThatThrownBy(() -> service.send(aliceScope, chatId, "oi"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ai, never()).reply(any(), anyString(), anyList(), anyString());
    }

    @Test
    void loadDeConversaDeOutroUsuarioRetorna404() {
        UUID chatId = UUID.randomUUID();
        when(store.require(bob, chatId)).thenThrow(new ResourceNotFoundException("chat.notFound"));

        assertThatThrownBy(() -> service.load(bob, chatId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Leitura ──────────────────────────────────────────────────────────

    @Test
    void sendAnexaParDeMensagensComHistoricoComoContexto() {
        UUID chatId = UUID.randomUUID();
        Chat chat = chat(chatId, alice, "Minha conversa");
        when(store.require(alice, chatId)).thenReturn(chat);
        List<ChatMessage> prior = List.of(
                message(chatId, ChatMessageRole.USER, "oi"),
                message(chatId, ChatMessageRole.ASSISTANT, "olá"));
        when(store.messagesOf(chatId)).thenReturn(prior);
        when(store.detail(chat)).thenReturn(new ChatDetailDto(chatId, "Minha conversa", List.of()));
        stubContext();
        when(ai.reply(eq(alice), eq("e agora?"), eq(prior), anyString())).thenReturn("resposta");

        ChatDetailDto detail = service.send(aliceScope, chatId, "e agora?");

        assertThat(detail.id()).isEqualTo(chatId);
        verify(ai).reply(eq(alice), eq("e agora?"), eq(prior), anyString());
        verify(store).append(chatId, ChatMessageRole.USER, "e agora?");
        verify(store).append(chatId, ChatMessageRole.ASSISTANT, "resposta");
    }

    @Test
    void loadComConversaAtivaMarcaHistoricoEDevolveMensagens() {
        UUID chatId = UUID.randomUUID();
        Chat chat = chat(chatId, alice, "Conversa A");
        when(store.require(alice, chatId)).thenReturn(chat);
        when(store.detail(chat)).thenReturn(new ChatDetailDto(chatId, "Conversa A", List.of(
                new ChatMessageDto(ChatMessageRole.USER, "oi", Instant.now(), false),
                new ChatMessageDto(ChatMessageRole.ASSISTANT, "olá", Instant.now(), true))));
        when(store.historyOf(alice)).thenReturn(List.of(chat));

        ManagerView view = service.load(alice, chatId);

        assertThat(view.hasActiveChat()).isTrue();
        assertThat(view.activeChat().messages()).hasSize(2);
        assertThat(view.activeChat().messages().get(1).assistant()).isTrue();
        assertThat(view.history()).hasSize(1);
        assertThat(view.history().get(0).active()).isTrue();
        assertThat(view.suggestions()).isNotEmpty();
    }

    @Test
    void loadSemConversaAtivaMostraBoasVindas() {
        when(store.historyOf(alice)).thenReturn(List.of());

        ManagerView view = service.load(alice, null);

        assertThat(view.hasActiveChat()).isFalse();
        assertThat(view.history()).isEmpty();
    }

    @Test
    void deleteDelegaAoStore() {
        UUID chatId = UUID.randomUUID();

        service.delete(alice, chatId);

        verify(store).delete(alice, chatId);
    }

    // ── Abertura a partir de uma tela ────────────────────────────────────

    @Test
    void startFromScreen_gravaOResumoComoPrimeiraFalaEPrefixaOTitulo() {
        UUID chatId = stubNewChat();
        stubContext();
        when(ai.reply(eq(alice), eq("Como reduzo isso?"), anyList(), anyString()))
                .thenReturn("Comece pelos fixos.");

        service.startFromScreen(aliceScope, "Visão geral",
                "Você gastou R$ 6.200,00 neste mês.", " Como reduzo isso? ");

        verify(store).create(alice, "Visão geral · Como reduzo isso?");
        ArgumentCaptor<String> contents = ArgumentCaptor.forClass(String.class);
        verify(store, times(3)).append(eq(chatId), any(), contents.capture());
        assertThat(contents.getAllValues()).containsExactly(
                "Você gastou R$ 6.200,00 neste mês.", "Como reduzo isso?", "Comece pelos fixos.");
    }

    @Test
    void startFromScreen_tituloLongoEhTruncadoNoLimiteDaColuna() {
        stubNewChat();
        stubContext();
        when(ai.reply(eq(alice), anyString(), anyList(), anyString())).thenReturn("ok");

        service.startFromScreen(aliceScope, "Cartões & Contas", "resumo", "x".repeat(200));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(store).create(eq(alice), title.capture());
        assertThat(title.getValue())
                .hasSizeLessThanOrEqualTo(ChatService.TITLE_MAX)
                .startsWith("Cartões & Contas · ")
                .endsWith("…");
    }

    @Test
    void startAndDetail_semResumo_gravaSoOParPerguntaResposta() {
        UUID chatId = stubNewChat();
        stubContext();
        when(ai.reply(eq(alice), eq("E aí?"), anyList(), anyString())).thenReturn("Tudo certo.");

        service.startAndDetail(aliceScope, "E aí?");

        verify(store).create(alice, "E aí?");
        verify(store, times(2)).append(eq(chatId), any(), anyString());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Conversa recém-criada com id fixo; devolve o id para as verificações.
     * {@code lenient} porque nem todo caminho chega a ler a thread de volta
     * (o de erro, por exemplo, para antes).
     */
    private UUID stubNewChat() {
        UUID chatId = UUID.randomUUID();
        Chat created = chat(chatId, alice, "…");
        when(store.create(eq(alice), anyString())).thenReturn(created);
        lenient().when(store.detail(created))
                .thenReturn(new ChatDetailDto(chatId, "…", List.of()));
        lenient().when(store.messagesOf(chatId)).thenReturn(List.of());
        return chatId;
    }

    private void stubContext() {
        when(contextBuilder.build(eq(alice), eq(period))).thenReturn("DADOS REAIS");
        when(semantic.search(eq(alice), anyString())).thenReturn(List.of());
        when(semantic.asPromptSection(anyList())).thenReturn("");
    }

    private static Chat chat(UUID id, UUID userId, String title) {
        Chat c = new Chat();
        c.setId(id);
        c.setUserId(userId);
        c.setTitle(title);
        c.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        return c;
    }

    private static ChatMessage message(UUID chatId, ChatMessageRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setChatId(chatId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(Instant.parse("2026-05-01T10:05:00Z"));
        return m;
    }
}
