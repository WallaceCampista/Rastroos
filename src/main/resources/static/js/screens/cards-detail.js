/* ─────────────────────────────────────────────────────────────
   Rastroo$ — /app/cards · detalhe da conta em modal
   ─────────────────────────────────────────────────────────────
   O clique no card é tratado pelo modals.js ([data-modal-url]):
   o detalhe abre no modal central, por cima dos cards. Aqui ficam:
     1. reabrir o detalhe depois de um redirect (?open=<conta>);
     2. ações dentro do modal SEM recarregar a página (pagar/reabrir
        fatura, marcar pago, excluir lançamento, lançar a fatura do
        mês aberto): o POST vai por fetch, os cards por trás são
        atualizados e o detalhe é redesenhado no lugar — o modal não
        fecha e reabre;
     3. anexar a fatura (📎) → leitura → conferência no modal;
     4. a conferência: marcar/desmarcar, total marcado, recálculo
        quando o vencimento muda de mês, e trava de duplo envio.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const MAX_BYTES = 8 * 1024 * 1024; // espelha extraction.max-file-size-bytes
    const Modal = window.RastroosModal;
    if (!Modal) return;

    // ── Abrir conteúdo [data-modal-content] no modal ─────────────────────

    const openHtml = (html) => {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        const content = doc.querySelector('[data-modal-content]');
        if (!content) return null;
        Modal.ensureStyles(doc);
        const node = document.importNode(content, true);
        Modal.openNode(node, { wide: content.hasAttribute('data-modal-wide') });
        return node;
    };

    const openUrl = async (url) => {
        try {
            const resp = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
            return openHtml(await resp.text());
        } catch (err) {
            return null;
        }
    };

    // Só card que está na tela: o id vindo da URL nunca vira URL arbitrária.
    const tileOf = (accountId) => Array.prototype.find.call(
        document.querySelectorAll('.card-tile[data-account-id]'),
        (t) => t.getAttribute('data-account-id') === accountId);

    const modalBody = () => document.querySelector('[data-modal-container] .modal-body');

    const screen = () => document.querySelector('.screen-cards');

    // ── 1. Reabrir depois do redirect ────────────────────────────────────

    document.addEventListener('DOMContentLoaded', () => {
        const params = new URLSearchParams(window.location.search);
        const accountId = params.get('open');
        if (!accountId) return;

        // Tira o parâmetro da barra: um F5 depois de fechar não reabre.
        params.delete('open');
        const query = params.toString();
        window.history.replaceState(null, '',
            window.location.pathname + (query ? '?' + query : '') + window.location.hash);

        const tile = tileOf(accountId);
        if (tile) openUrl(tile.getAttribute('data-modal-url'));
    });

    // ── 2. Ações no modal sem recarregar a página ────────────────────────

    /**
     * Atualiza o que está por trás do modal com a página que o servidor
     * devolveu depois do redirect: valores dos KPIs, a grade de cards, os
     * chips de mês da topbar e o streak da sidebar. Elementos com ouvinte
     * próprio ficam (a inclinação dos KPIs e o indicador dos chips).
     */
    const refreshScreen = (doc) => {
        const current = screen();
        const fresh = doc.querySelector('.screen-cards');
        if (!current || !fresh) return false;

        const values = current.querySelectorAll('.dash-kpis .kpi-value');
        const freshValues = fresh.querySelectorAll('.dash-kpis .kpi-value');
        if (values.length === freshValues.length) {
            values.forEach((el, i) => {
                el.textContent = freshValues[i].textContent;
                if (freshValues[i].hasAttribute('data-amount')) {
                    el.setAttribute('data-amount', freshValues[i].getAttribute('data-amount'));
                }
            });
        }

        const grid = current.querySelector('.cards-grid');
        const freshGrid = fresh.querySelector('.cards-grid');
        if (grid && freshGrid) {
            const node = document.importNode(freshGrid, true);
            grid.replaceWith(node);
            Modal.paintBindings(node);
            document.dispatchEvent(new CustomEvent('rastroos:cards-refreshed'));
        }

        const chips = document.querySelectorAll('.ps-track .ps-chip');
        const freshChips = doc.querySelectorAll('.ps-track .ps-chip');
        if (chips.length > 0 && chips.length === freshChips.length) {
            chips.forEach((chip, i) => {
                chip.className = freshChips[i].className;
                chip.replaceChildren(...Array.from(freshChips[i].childNodes,
                    (n) => document.importNode(n, true)));
            });
        }

        const streak = document.querySelector('.streak-stats');
        const freshStreak = doc.querySelector('.streak-stats');
        if (streak && freshStreak) streak.replaceWith(document.importNode(freshStreak, true));
        return true;
    };

    /**
     * Regra do protótipo: pagar a fatura ou marcar um lançamento como pago solta
     * emojis conforme a situação da conta NO MOMENTO DO CLIQUE — vencida 🥴,
     * vence em breve 😮‍💨, no prazo 😍. Reabrir/desmarcar não comemora.
     */
    const celebrationFor = (form) => {
        const detail = form.closest('[data-account-status]');
        const button = form.querySelector('.pay-invoice, .paid-toggle');
        if (!detail || !button || button.classList.contains('is-paid')) return null;
        const status = detail.getAttribute('data-account-status');
        const emoji = status === 'overdue' ? '🥴' : status === 'soon' ? '😮‍💨' : '😍';
        return { emojis: [emoji], count: form.classList.contains('pay-invoice-form') ? 22 : 16 };
    };

    // Mesmo caminho do toast.js no carregamento da página: toast + evento com a
    // chave. Aqui a comemoração é a da regra acima, então o evento sai marcado
    // para o celebrate.js não soltar a festa genérica junto.
    const announce = (doc, celebration) => {
        let succeeded = false;
        doc.querySelectorAll('.screen > .flash').forEach((el) => {
            const detail = {
                key: el.getAttribute('data-flash-key'),
                kind: el.classList.contains('flash-error') ? 'error' : 'ok',
                text: el.textContent.trim(),
                celebrated: true,
            };
            if (detail.kind === 'ok') succeeded = true;
            if (window.RastroosToast) window.RastroosToast.show(detail.text, detail.kind);
            document.dispatchEvent(new CustomEvent('rastroos:flash', { detail: detail }));
        });
        if (celebration && succeeded && window.RastroosCelebrate) {
            window.RastroosCelebrate.celebrate(celebration.emojis, celebration.count);
        }
    };

    const submitInPlace = async (form) => {
        const celebration = celebrationFor(form); // situação de ANTES de pagar
        const body = modalBody();
        const scroll = body ? body.scrollTop : 0;
        // urlencoded: a conferência de uma fatura longa passa do limite de partes do multipart.
        const payload = new URLSearchParams(new FormData(form));
        form.querySelectorAll('button[type="submit"]').forEach((b) => { b.disabled = true; });

        let resp;
        let html;
        try {
            resp = await fetch(form.action, {
                method: 'POST',
                body: payload,
                headers: { 'X-Requested-With': 'fetch' },
            });
            html = await resp.text();
        } catch (err) {
            // Não dá para saber se o servidor gravou: mostra o estado real em vez
            // de reenviar (pagar duas vezes reabriria a fatura).
            window.location.reload();
            return;
        }

        const doc = new DOMParser().parseFromString(html, 'text/html');
        const target = new URL(resp.url, window.location.href);
        const sameScreen = resp.redirected && target.pathname === '/app/cards'
            && screen() && target.searchParams.get('ym') === screen().getAttribute('data-period');
        if (sameScreen && refreshScreen(doc)) {
            const tile = tileOf(target.searchParams.get('open'));
            if (tile) {
                const node = await openUrl(tile.getAttribute('data-modal-url'));
                const reopened = modalBody();
                if (node && reopened) reopened.scrollTop = scroll;
            } else {
                Modal.close(true);
            }
            announce(doc, celebration);
            return;
        }
        // O servidor devolveu o próprio corpo do modal (ex.: conferência com erro).
        if (openHtml(html)) return;
        window.location.href = target.href;
    };

    /** Fatura de outro mês leva a outra tela: essa segue pela navegação normal. */
    const samePeriod = (form) => {
        const due = form.querySelector('[name="dueDate"]');
        const current = screen();
        if (!due || !current) return true;
        return (due.value || '').slice(0, 7) === current.getAttribute('data-period');
    };

    document.addEventListener('submit', (e) => {
        const form = e.target;
        if (!form.closest('[data-modal-container]')) return;
        const review = form.hasAttribute('data-invoice-review');
        if (!review && !form.hasAttribute('data-cards-inplace')) return;

        if (form.getAttribute('data-sending') === 'true') {
            e.preventDefault(); // duplo clique — o servidor também não duplicaria
            return;
        }
        form.setAttribute('data-sending', 'true');
        if (review) {
            const submit = form.querySelector('[data-invoice-submit]');
            // Desabilitar no próprio evento cancelaria o envio nativo em alguns navegadores.
            if (submit) setTimeout(() => { submit.disabled = true; submit.textContent = 'Lançando…'; }, 0);
            if (!samePeriod(form)) return;
        }
        e.preventDefault();
        submitInPlace(form);
    });

    // ── 3. Anexar fatura ─────────────────────────────────────────────────

    const uploadParts = (form) => ({
        loading: form.querySelector('[data-invoice-loading]'),
        error: form.querySelector('[data-invoice-error]'),
        csrf: form.querySelector('input[name="_csrf"]'),
        ym: form.querySelector('input[name="ym"]'),
    });

    const setBusy = (form, busy) => {
        const parts = uploadParts(form);
        if (parts.loading) parts.loading.hidden = !busy;
        const root = form.closest('[data-modal-container]') || document;
        root.querySelectorAll('[data-invoice-attach]').forEach((b) => { b.disabled = busy; });
    };

    const showError = (form, message) => {
        const parts = uploadParts(form);
        if (!parts.error) return;
        parts.error.textContent = message;
        parts.error.hidden = !message;
    };

    const upload = async (form, file) => {
        showError(form, '');
        if (file.size > MAX_BYTES) {
            showError(form, 'Arquivo muito grande (máximo 8 MB).');
            return;
        }
        const parts = uploadParts(form);
        const body = new FormData();
        body.append('file', file);
        if (parts.ym) body.append('ym', parts.ym.value);
        const headers = { 'X-Requested-With': 'fetch' };
        if (parts.csrf) {
            body.append('_csrf', parts.csrf.value);
            headers['X-CSRF-TOKEN'] = parts.csrf.value; // multipart: o CsrfFilter lê o cabeçalho
        }

        setBusy(form, true);
        try {
            const resp = await fetch(form.action, { method: 'POST', body: body, headers: headers });
            if (!openHtml(await resp.text())) {
                setBusy(form, false);
                showError(form, 'Não consegui ler a fatura agora. Tente de novo em instantes.');
            }
        } catch (err) {
            setBusy(form, false);
            showError(form, 'Falha ao enviar o arquivo. Verifique a conexão e tente de novo.');
        }
    };

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-invoice-attach]');
        if (!btn) return;
        e.preventDefault();
        const root = btn.closest('[data-modal-content]') || document;
        const input = root.querySelector('[data-invoice-file]');
        if (input) input.click();
    });

    document.addEventListener('change', (e) => {
        const input = e.target.closest('[data-invoice-file]');
        if (!input) return;
        const form = input.closest('[data-invoice-upload]');
        const file = input.files && input.files[0];
        input.value = ''; // permite anexar o mesmo arquivo de novo
        if (form && file) upload(form, file);
    });

    // ── 4. Conferência ───────────────────────────────────────────────────

    const brl = (cents) => 'R$ ' + (cents / 100).toLocaleString('pt-BR',
        { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const refreshTotals = (form) => {
        const boxes = form.querySelectorAll('[data-invoice-select]');
        let cents = 0;
        let count = 0;
        boxes.forEach((box) => {
            if (!box.checked) return;
            cents += parseInt(box.getAttribute('data-cents'), 10) || 0;
            count += 1;
        });
        const sum = form.querySelector('[data-invoice-selected-sum]');
        if (sum) sum.textContent = brl(cents);
        const counter = form.querySelector('[data-invoice-selected-count]');
        if (counter) counter.textContent = String(count);
        const submit = form.querySelector('[data-invoice-submit]');
        if (submit) submit.disabled = count === 0;
        const all = form.querySelector('[data-invoice-toggle-all]');
        if (all) {
            all.checked = boxes.length > 0 && count === boxes.length;
            all.indeterminate = count > 0 && count < boxes.length;
        }
    };

    document.addEventListener('rastroos:modal-opened', (e) => {
        const container = e.detail && e.detail.container;
        if (!container) return;
        container.querySelectorAll('[data-invoice-review]').forEach(refreshTotals);
    });

    document.addEventListener('change', (e) => {
        const form = e.target.closest('[data-invoice-review]');
        if (!form) return;
        if (e.target.matches('[data-invoice-toggle-all]')) {
            form.querySelectorAll('[data-invoice-select]').forEach((box) => { box.checked = e.target.checked; });
        }
        if (e.target.matches('[data-invoice-select], [data-invoice-toggle-all]')) {
            refreshTotals(form);
        }
        if (e.target.matches('[data-invoice-due]')) {
            recalculate(form, e.target);
        }
    });

    // O vencimento define o mês em que os lançamentos caem — e, portanto, o que
    // já está lançado. Mudou o mês, o cruzamento é refeito no servidor.
    const recalculate = async (form, input) => {
        const month = (input.value || '').slice(0, 7);
        if (!month || month === input.getAttribute('data-month')) return;
        const submit = form.querySelector('[data-invoice-submit]');
        if (submit) submit.disabled = true;
        try {
            const body = new URLSearchParams(new FormData(form));
            const resp = await fetch(form.getAttribute('data-review-url'), {
                method: 'POST',
                body: body,
                headers: { 'X-Requested-With': 'fetch' },
            });
            if (!openHtml(await resp.text()) && submit) submit.disabled = false;
        } catch (err) {
            if (submit) submit.disabled = false;
        }
    };
})();
