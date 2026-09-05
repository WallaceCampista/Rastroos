/* ─────────────────────────────────────────────────────────────
   Rastro$ — widget flutuante do Alfredo
   ─────────────────────────────────────────────────────────────
   Fluxo:
   1. a página carrega → o orbe entra em "pensando" (ondas rápidas)
      e o resumo da tela é buscado em GET /api/v1/insights/{tela};
   2. o resumo chega → o orbe se acalma e o balão abre, com a linha
      do tempo do rodapé consumindo o tempo de exibição (o fim da
      animação é o gatilho do fechamento — pausar no hover pausa a
      contagem, sem timer paralelo);
   3. clicar no texto (ou no orbe) abre o chat flutuante já com o
      resumo como primeira fala do Alfredo;
   4. a primeira pergunta cria uma conversa de verdade — por isso
      ela aparece no histórico da tela /app/manager.

   Telas sem resumo (data-screen ausente) só ganham o atalho de chat.
   Todo texto vindo do servidor entra por textContent (nunca innerHTML).
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const root = document.querySelector('[data-alfredo]');
    if (!root) return;

    /** Tempo mínimo de "pensando" para a animação de entrada ser vista. */
    const INTRO_MS = 1500;
    /** Duração da exibição do balão (a linha do tempo espelha esse valor). */
    const BUBBLE_MS = 17000;

    const cfg = {
        screen: root.dataset.screen || null,
        screenLabel: root.dataset.screenLabel || '',
        ym: root.dataset.ym || null,
        csrf: root.dataset.csrf || null,
        insightUrl: root.dataset.insightUrl || '/api/v1/insights',
        chatUrl: root.dataset.chatUrl || '/api/v1/chats',
        managerUrl: root.dataset.managerUrl || '/app/manager',
        initial: root.dataset.userInitial || '·'
    };

    const orb        = root.querySelector('[data-alf-orb]');
    const badge      = root.querySelector('[data-alf-orb-badge]');
    const bubble     = root.querySelector('[data-alf-bubble]');
    const bubbleText = root.querySelector('[data-alf-bubble-text]');
    const summaryEl  = root.querySelector('[data-alf-bubble-summary]');
    const bubbleHide = root.querySelector('[data-alf-bubble-close]');
    const timeline   = root.querySelector('[data-alf-timeline]');
    const chat       = root.querySelector('[data-alf-chat]');
    const chatSub    = root.querySelector('[data-alf-chat-sub]');
    const thread     = root.querySelector('[data-alf-chat-thread]');
    const chatForm   = root.querySelector('[data-alf-chat-form]');
    const chatInput  = root.querySelector('[data-alf-chat-input]');
    const chatSend   = root.querySelector('[data-alf-chat-send]');
    const chatClose  = root.querySelector('[data-alf-chat-close]');
    const chatNew    = root.querySelector('[data-alf-chat-new]');
    const chatOpen   = root.querySelector('[data-alf-chat-open]');
    const chatError  = root.querySelector('[data-alf-chat-error]');

    /** Estado local: resumo carregado, conversa criada e envio em curso. */
    const state = { summary: null, chatId: null, sending: false, seeded: false };

    // ── Rede ─────────────────────────────────────────────────────

    const jsonHeaders = () => {
        const headers = { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' };
        if (cfg.csrf) headers['X-CSRF-TOKEN'] = cfg.csrf;
        return headers;
    };

    const withYm = (url) => (cfg.ym ? `${url}?ym=${encodeURIComponent(cfg.ym)}` : url);

    const fetchInsight = async () => {
        if (!cfg.screen) return null;
        const url = withYm(`${cfg.insightUrl}/${encodeURIComponent(cfg.screen)}`);
        const resp = await fetch(url, {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'fetch' },
            credentials: 'same-origin'
        });
        if (!resp.ok) return null;
        const data = await resp.json();
        return data && data.text ? data : null;
    };

    const postJson = async (url, body) => {
        const resp = await fetch(url, {
            method: 'POST',
            headers: jsonHeaders(),
            credentials: 'same-origin',
            body: JSON.stringify(body)
        });
        if (!resp.ok) {
            const error = new Error(`HTTP ${resp.status}`);
            error.status = resp.status;
            throw error;
        }
        return resp.json();
    };

    // ── Balão ────────────────────────────────────────────────────

    const stopTimeline = () => {
        bubble.classList.remove('is-timing');
        // Reinicia a animação: sem o reflow o navegador reaproveita o estado antigo.
        void timeline.offsetWidth;
    };

    const startTimeline = () => {
        stopTimeline();
        timeline.style.setProperty('--alf-timeout', `${BUBBLE_MS}ms`);
        bubble.classList.add('is-timing');
    };

    const showBubble = (text) => {
        summaryEl.textContent = text;
        bubble.classList.remove('is-leaving');
        bubble.hidden = false;
        badge.hidden = true;
        startTimeline();
    };

    const hideBubble = (markUnread) => {
        if (bubble.hidden) return;
        stopTimeline();
        bubble.classList.add('is-leaving');
        // animationend borbulha: só a animação do próprio balão conclui o fecho
        // (por isso o listener não pode ser `once`, senão um evento de filho
        // o consumiria antes da hora).
        const done = () => {
            bubble.removeEventListener('animationend', onEnd);
            bubble.hidden = true;
            bubble.classList.remove('is-leaving');
        };
        const onEnd = (e) => {
            if (e.target === bubble) done();
        };
        bubble.addEventListener('animationend', onEnd);
        // Rede de segurança: se a animação não disparar (aba oculta), some assim mesmo.
        window.setTimeout(done, 400);
        if (markUnread && state.summary) badge.hidden = false;
    };

    timeline.addEventListener('animationend', () => hideBubble(true));

    bubbleHide.addEventListener('click', () => hideBubble(true));

    bubbleText.addEventListener('click', () => openChat());

    // ── Chat ─────────────────────────────────────────────────────

    const appendMessage = (role, text) => {
        const wrap = document.createElement('div');
        wrap.className = `alf-msg alf-msg-${role === 'user' ? 'user' : 'asst'}`;

        const avatar = document.createElement('span');
        avatar.className = 'alf-msg-avatar';
        avatar.setAttribute('aria-hidden', 'true');
        avatar.textContent = role === 'user' ? cfg.initial : '✦';

        const body = document.createElement('div');
        body.className = 'alf-msg-bubble';
        body.textContent = text;

        wrap.append(avatar, body);
        thread.appendChild(wrap);
        thread.scrollTop = thread.scrollHeight;
        return wrap;
    };

    const appendTyping = () => {
        const wrap = appendMessage('assistant', '');
        const body = wrap.querySelector('.alf-msg-bubble');
        body.textContent = '';
        const dots = document.createElement('span');
        dots.className = 'alf-typing';
        dots.append(document.createElement('span'),
                    document.createElement('span'),
                    document.createElement('span'));
        body.appendChild(dots);
        thread.scrollTop = thread.scrollHeight;
        return wrap;
    };

    /** Última fala do Alfredo na thread devolvida pelo servidor. */
    const lastAnswer = (detail) => {
        const messages = (detail && detail.messages) || [];
        for (let i = messages.length - 1; i >= 0; i -= 1) {
            if (messages[i].assistant) return messages[i].content;
        }
        return '';
    };

    const showError = (message) => {
        chatError.textContent = message;
        chatError.hidden = false;
    };

    const clearError = () => {
        chatError.textContent = '';
        chatError.hidden = true;
    };

    /** Altura máxima do campo antes de virar rolagem (espelha o CSS). */
    const INPUT_MAX_H = 110;

    /**
     * Auto-crescimento do campo. `scrollHeight` não inclui a borda e o projeto
     * usa `box-sizing: border-box`, então sem somá-la sobram 2px de overflow e
     * aparece barra de rolagem num campo de uma linha só.
     */
    const growInput = () => {
        chatInput.style.height = 'auto';
        const cs = getComputedStyle(chatInput);
        const border = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderBottomWidth);
        const wanted = chatInput.scrollHeight + border;
        chatInput.style.height = `${Math.min(wanted, INPUT_MAX_H)}px`;
        chatInput.style.overflowY = wanted > INPUT_MAX_H ? 'auto' : 'hidden';
    };

    const setSending = (on) => {
        state.sending = on;
        chatSend.disabled = on;
        chatInput.disabled = on;
        chatNew.disabled = on;
    };

    const linkToManager = () => {
        if (!state.chatId) return;
        chatOpen.href = `${cfg.managerUrl}?chat=${encodeURIComponent(state.chatId)}`;
        chatOpen.hidden = false;
    };

    function openChat() {
        hideBubble(false);
        badge.hidden = true;
        chat.classList.remove('is-leaving');
        chat.hidden = false;
        orb.setAttribute('aria-expanded', 'true');
        chatSub.textContent = cfg.screenLabel || 'Gerente financeiro';

        seedThread();
        chatInput.focus();
    }

    /** Primeira fala do Alfredo: o resumo da tela, ou a saudação padrão. */
    const seedThread = () => {
        if (state.seeded) return;
        state.seeded = true;
        appendMessage('assistant', state.summary
            || 'Oi! Sou o Alfredo. Pergunte o que quiser sobre o seu dinheiro.');
    };

    /**
     * Recomeça do zero: solta o vínculo com a conversa atual para que a próxima
     * pergunta crie outra. A conversa anterior já está gravada — continua no
     * histórico da tela do Alfredo, nada se perde.
     */
    const newChat = () => {
        if (state.sending) return;
        state.chatId = null;
        state.seeded = false;
        thread.replaceChildren();
        chatInput.value = '';
        growInput();
        chatOpen.hidden = true;
        clearError();
        seedThread();
        chatInput.focus();
    };

    const closeChat = () => {
        if (chat.hidden) return;
        chat.classList.add('is-leaving');
        orb.setAttribute('aria-expanded', 'false');
        const done = () => {
            chat.removeEventListener('animationend', onEnd);
            chat.hidden = true;
            chat.classList.remove('is-leaving');
        };
        const onEnd = (e) => {
            if (e.target === chat) done();
        };
        chat.addEventListener('animationend', onEnd);
        window.setTimeout(done, 400);
    };

    chatClose.addEventListener('click', closeChat);
    chatNew.addEventListener('click', newChat);

    // Clique fora do widget fecha o chat. closeChat() só esconde: a thread e o
    // que estiver digitado continuam no DOM e voltam intactos ao reabrir.
    document.addEventListener('click', (e) => {
        if (chat.hidden) return;
        if (root.contains(e.target)) return; // orbe/balão/chat têm o próprio comportamento
        closeChat();
    });

    orb.addEventListener('click', () => {
        if (!chat.hidden) {
            closeChat();
            return;
        }
        openChat();
    });

    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        if (!chat.hidden) closeChat();
        else hideBubble(true);
    });

    // ── Envio ────────────────────────────────────────────────────

    const send = async (message) => {
        clearError();
        setSending(true);
        appendMessage('user', message);
        const typing = appendTyping();

        try {
            const detail = state.chatId
                ? await postJson(`${cfg.chatUrl}/${encodeURIComponent(state.chatId)}/messages`,
                                 { message })
                : await postJson(
                    state.summary && cfg.screen
                        ? withYm(`${cfg.insightUrl}/${encodeURIComponent(cfg.screen)}/chat`)
                        : cfg.chatUrl,
                    { message });

            state.chatId = detail.id;
            // Troca o "digitando" pela resposta: mantém a thread local intacta
            // (a persistida é a mesma) e evita o pisca de um re-render inteiro.
            typing.querySelector('.alf-msg-bubble').textContent = lastAnswer(detail);
            thread.scrollTop = thread.scrollHeight;
            linkToManager();
        } catch (err) {
            typing.remove();
            showError(err.status === 401 || err.status === 403
                ? 'Sua sessão expirou. Recarregue a página e tente de novo.'
                : 'Não consegui falar com o Alfredo agora. Tente novamente em instantes.');
        } finally {
            setSending(false);
            chatInput.focus();
        }
    };

    chatForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const message = chatInput.value.trim();
        if (!message || state.sending) return;
        chatInput.value = '';
        growInput();
        send(message);
    });

    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            chatForm.requestSubmit();
        }
    });

    chatInput.addEventListener('input', growInput);

    // ── Boot ─────────────────────────────────────────────────────

    const settle = () => orb.classList.remove('is-thinking');

    const boot = async () => {
        const started = Date.now();
        let insight = null;
        try {
            insight = await fetchInsight();
        } catch (err) {
            insight = null; // resumo é acessório: o orbe continua sendo atalho de chat
        }

        const wait = Math.max(0, INTRO_MS - (Date.now() - started));
        window.setTimeout(() => {
            settle();
            if (!insight) return;
            state.summary = insight.text;
            // "Ocultar valores" borra o texto: não faz sentido abrir sozinho,
            // então o resumo fica disponível pelo orbe (ponto de aviso).
            if (document.body.classList.contains('values-hidden')) {
                badge.hidden = false;
                return;
            }
            showBubble(insight.text);
        }, wait);
    };

    boot();
})();
