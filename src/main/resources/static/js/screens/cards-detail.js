/* ─────────────────────────────────────────────────────────────
   Rastroo$ — /app/cards · detalhe da conta em modal
   ─────────────────────────────────────────────────────────────
   O clique no card é tratado pelo modals.js ([data-modal-url]):
   o detalhe abre no modal central, por cima dos cards. Aqui ficam:
     1. reabrir o detalhe depois de um redirect (?open=<conta>),
        mostrando dentro dele a mensagem que o servidor mandou —
        o toast fica atrás do fundo do modal;
     2. anexar a fatura (📎) → leitura → conferência no modal;
     3. a conferência: marcar/desmarcar, total marcado, recálculo
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

    const note = (text, kind) => {
        const box = document.createElement('div');
        box.className = 'acct-note' + (kind === 'error' ? ' is-error' : ' is-ok');
        box.setAttribute('role', kind === 'error' ? 'alert' : 'status');
        box.textContent = text;
        return box;
    };

    // ── 1. Reabrir depois do redirect ────────────────────────────────────

    // O toast.js promove o .flash no DOMContentLoaded e avisa por evento;
    // guardamos o texto para repetir dentro do modal reaberto.
    const flashes = [];
    document.addEventListener('rastroos:flash', (e) => {
        if (e.detail && e.detail.text) flashes.push(e.detail);
    });

    document.addEventListener('DOMContentLoaded', async () => {
        const params = new URLSearchParams(window.location.search);
        const accountId = params.get('open');
        if (!accountId) return;

        // Tira o parâmetro da barra: um F5 depois de fechar não reabre.
        params.delete('open');
        const query = params.toString();
        window.history.replaceState(null, '',
            window.location.pathname + (query ? '?' + query : '') + window.location.hash);

        // Só abre card que está na tela: o parâmetro nunca vira URL arbitrária.
        const tile = Array.prototype.find.call(
            document.querySelectorAll('.card-tile[data-account-id]'),
            (t) => t.getAttribute('data-account-id') === accountId);
        if (!tile) return;

        const node = await openUrl(tile.getAttribute('data-modal-url'));
        if (!node) return;
        const top = node.querySelector('.acct-detail-top');
        flashes.forEach((f) => {
            const box = note(f.text, f.kind);
            if (top) top.after(box); else node.prepend(box);
        });
    });

    // ── 2. Anexar fatura ─────────────────────────────────────────────────

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

    // ── 3. Conferência ───────────────────────────────────────────────────

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
            // urlencoded, não multipart: uma fatura longa passa do limite de partes do Tomcat.
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

    document.addEventListener('submit', (e) => {
        const form = e.target.closest('[data-invoice-review]');
        if (!form) return;
        const submit = form.querySelector('[data-invoice-submit]');
        if (form.getAttribute('data-sending') === 'true') {
            e.preventDefault(); // duplo clique — o servidor também não duplicaria
            return;
        }
        form.setAttribute('data-sending', 'true');
        if (submit) {
            // Desabilitar no próprio evento cancelaria o envio em alguns navegadores.
            setTimeout(() => { submit.disabled = true; submit.textContent = 'Lançando…'; }, 0);
        }
    });
})();
