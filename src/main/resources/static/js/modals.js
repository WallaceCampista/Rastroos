/* ─────────────────────────────────────────────────────────────
   Rastro$ — modais (overlay sobre a página atual)
   ─────────────────────────────────────────────────────────────
   Elementos com [data-modal-url] abrem o formulário daquela rota
   por cima da página (sem navegar). A estratégia é 100% client-side:
     1. fetch da rota → HTML da página do formulário;
     2. injeta os <link> de CSS que a página usa (ex.: expenses.css,
        auth.css) — senão o form ficaria sem estilo;
     3. extrai só o <form> do conteúdo e o exibe no overlay;
     4. o submit vai por fetch: se o servidor redireciona (sucesso),
        fecha e recarrega a página; se retorna o form (erros de
        validação), re-injeta no modal.
   Degrada graciosamente: sem JS / falha de rede → navega normal.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const backdrop = document.querySelector('[data-modal-backdrop]');
    const container = document.querySelector('[data-modal-container]');
    if (!backdrop || !container) return;

    const FORM_SELECTOR = '.app-content form, .auth-card form';
    const TITLE_SELECTOR = '.screen-title, .auth-title';

    const ensureStyles = (doc) => {
        doc.querySelectorAll('head link[rel="stylesheet"]').forEach((link) => {
            const href = link.getAttribute('href');
            if (!href || document.querySelector('link[href="' + href + '"]')) return;
            const l = document.createElement('link');
            l.rel = 'stylesheet';
            l.href = href;
            document.head.appendChild(l);
        });
    };

    const onKey = (e) => { if (e.key === 'Escape') closeModal(); };

    function closeModal() {
        backdrop.hidden = true;
        container.innerHTML = '';
        container.classList.remove('modal-wide');
        document.removeEventListener('keydown', onKey);
    }

    // Estilos dinâmicos sem inline (CSP): cor/preenchimento via CSSOM.
    function paintBindings(root) {
        root.querySelectorAll('[data-detail-tint]').forEach((el) => {
            const c = el.getAttribute('data-detail-tint');
            if (c) el.style.background =
                'linear-gradient(135deg, color-mix(in oklch, ' + c + ' 20%, transparent), transparent)';
        });
        root.querySelectorAll('[data-fill]').forEach((el) => {
            el.style.background = el.getAttribute('data-fill');
        });
        root.querySelectorAll('[data-color-text]').forEach((el) => {
            el.style.color = el.getAttribute('data-color-text');
        });
        root.querySelectorAll('[data-bar-width]').forEach((el) => {
            el.style.width = el.getAttribute('data-bar-width') + '%';
        });
    }

    // Abre um nó arbitrário (não-formulário) no overlay — ex.: detalhe de
    // investimento, que já traz o próprio cabeçalho e rodapé.
    function openNode(node, opts) {
        opts = opts || {};
        container.innerHTML = '';
        container.classList.toggle('modal-wide', !!opts.wide);
        const body = document.createElement('div');
        body.className = 'modal-body';
        body.appendChild(node);
        container.appendChild(body);
        container.querySelectorAll('[data-modal-close], [data-invest-detail-close], [data-account-detail-close]')
            .forEach((el) => el.addEventListener('click', (ev) => { ev.preventDefault(); closeModal(); }));
        container.querySelectorAll('form[data-confirm]').forEach((f) => {
            f.addEventListener('submit', (e) => {
                if (!window.confirm(f.getAttribute('data-confirm') || 'Confirmar?')) e.preventDefault();
            });
        });
        paintBindings(container);
        if (window.RastroosForms) window.RastroosForms.init(container);
        backdrop.hidden = false;
        document.addEventListener('keydown', onKey);
    }

    // Ajuda dinâmica do campo de parcelas (equivalente ao transaction-form.js,
    // que não roda dentro do modal).
    const wireInstallments = () => {
        const input = container.querySelector('#installments');
        if (!input) return;
        const help = input.parentNode.querySelector('.field-help');
        if (!help) return;
        const def = help.textContent;
        const update = () => {
            const n = parseInt(input.value, 10);
            help.textContent = (isFinite(n) && n > 1)
                ? 'Serão criados ' + n + ' lançamentos, um por mês, a partir da data informada.'
                : def;
        };
        input.addEventListener('input', update);
        update();
    };

    const render = (title, form) => {
        container.innerHTML = '';

        const head = document.createElement('div');
        head.className = 'modal-head';
        const h3 = document.createElement('h3');
        h3.textContent = title || 'Rastro$';
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'modal-close';
        close.setAttribute('aria-label', 'Fechar');
        close.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" '
            + 'stroke="currentColor" stroke-width="2" stroke-linecap="round" '
            + 'stroke-linejoin="round" aria-hidden="true"><path d="M6 6l12 12M6 18L18 6"/></svg>';
        close.addEventListener('click', closeModal);
        head.appendChild(h3);
        head.appendChild(close);

        const body = document.createElement('div');
        body.className = 'modal-body';
        body.appendChild(form);

        // Links de navegação de página cheia não fazem sentido no modal.
        form.querySelectorAll('.auth-links').forEach((el) => el.remove());

        container.appendChild(head);
        container.appendChild(body);

        container.querySelectorAll('[data-modal-close]').forEach((el) => {
            el.addEventListener('click', (ev) => { ev.preventDefault(); closeModal(); });
        });

        form.addEventListener('submit', onSubmit);
        if (window.RastroosForms) window.RastroosForms.init(container);
        wireInstallments();
        const first = form.querySelector('input:not([type=hidden]):not([readonly]), select, textarea');
        if (first) first.focus();
    };

    const onSubmit = async (e) => {
        e.preventDefault();
        const form = e.currentTarget;
        try {
            const resp = await fetch(form.action, {
                method: 'POST',
                body: new FormData(form),
                headers: { 'X-Requested-With': 'fetch' },
            });
            if (resp.redirected) { closeModal(); window.location.reload(); return; }
            const doc = new DOMParser().parseFromString(await resp.text(), 'text/html');
            const newForm = doc.querySelector(FORM_SELECTOR);
            if (newForm) {
                ensureStyles(doc);
                const t = doc.querySelector(TITLE_SELECTOR);
                render(t ? t.textContent.trim() : '', newForm);
            } else {
                closeModal();
                window.location.reload();
            }
        } catch (err) {
            form.submit(); // rede falhou → deixa navegar
        }
    };

    const closeUserMenu = () => {
        const dd = document.querySelector('[data-user-menu-dropdown]');
        if (dd) dd.hidden = true;
        const trg = document.querySelector('[data-user-menu-trigger]');
        if (trg) { trg.classList.remove('is-open'); trg.setAttribute('aria-expanded', 'false'); }
    };

    const openModal = async (url) => {
        closeUserMenu();
        try {
            const resp = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
            const doc = new DOMParser().parseFromString(await resp.text(), 'text/html');
            const form = doc.querySelector(FORM_SELECTOR);
            if (form) {
                ensureStyles(doc);
                const t = doc.querySelector(TITLE_SELECTOR);
                render(t ? t.textContent.trim() : '', form);
                backdrop.hidden = false;
                document.addEventListener('keydown', onKey);
                return;
            }
            // Conteúdo não-formulário (ex.: histórico de login): a página traz o
            // próprio cabeçalho/rodapé dentro de [data-modal-content].
            const content = doc.querySelector('[data-modal-content]');
            if (content) {
                ensureStyles(doc);
                openNode(document.importNode(content, true),
                         { wide: content.hasAttribute('data-modal-wide') });
                return;
            }
            window.location.href = url;
        } catch (err) {
            window.location.href = url;
        }
    };

    // Delegação: também cobre links [data-modal-url] injetados (ex.: "Editar"
    // dentro do modal de detalhe do investimento).
    document.addEventListener('click', (e) => {
        const el = e.target.closest('[data-modal-url]');
        if (!el) return;
        e.preventDefault();
        openModal(el.getAttribute('href') || el.getAttribute('data-modal-url'));
    });

    backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeModal(); });

    // API para outros scripts abrirem conteúdo próprio no overlay.
    window.RastroosModal = { openNode: openNode, close: closeModal, ensureStyles: ensureStyles };
})();
