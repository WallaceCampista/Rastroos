/* ─────────────────────────────────────────────────────────────
   Rastroo$ — wizard de boas-vindas (primeiro acesso)
   ─────────────────────────────────────────────────────────────
   O servidor marca a página com [data-onboarding-trigger] enquanto
   users.onboarding_completed_at for NULL. Aqui buscamos /app/onboarding,
   abrimos o [data-modal-content] num modal TRAVADO (só "Pular" ou
   "Concluir" fecham) e navegamos entre os passos por fetch:

     • links [data-onb-step]  → GET do passo, troca o conteúdo;
     • submits                → POST; se o servidor redirecionar de volta
                                para /app/onboarding, troca o conteúdo;
                                qualquer outro destino = terminou → fecha
                                e recarrega a tela.

   Sem JS nada disso é necessário: os mesmos links e forms navegam para a
   página cheia do wizard.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const CONTENT = '[data-modal-content]';
    const ONB_PATH = '/app/onboarding';

    const isOnboardingUrl = (url) => {
        try {
            return new URL(url, window.location.origin).pathname.startsWith(ONB_PATH);
        } catch (e) {
            return false;
        }
    };

    const parse = (html) => new DOMParser().parseFromString(html, 'text/html');

    function finish() {
        if (window.RastroosModal) window.RastroosModal.close(true);
        window.location.reload();
    }

    // ── Paleta: amostras montadas a partir de palettes.js ────────────────
    function wirePalette(root) {
        const grid = root.querySelector('[data-onb-palette]');
        const input = root.querySelector('[data-onb-palette-input]');
        if (!grid || !input) return;

        const palettes = window.RastroosPalettes || [];
        const count = Math.min(parseInt(grid.getAttribute('data-count'), 10) || palettes.length,
                               palettes.length);
        let selected = parseInt(grid.getAttribute('data-selected'), 10);
        if (Number.isNaN(selected) || selected < 0 || selected >= count) selected = 0;

        const tiles = [];
        for (let i = 0; i < count; i++) {
            const pair = palettes[i];
            const tile = document.createElement('button');
            tile.type = 'button';
            tile.className = 'onb-swatch';
            tile.setAttribute('aria-label', 'Paleta ' + (i + 1));
            const primary = document.createElement('span');
            primary.className = 'onb-swatch-primary';
            primary.style.background = pair[0];   // CSSOM: compatível com a CSP
            const accent = document.createElement('span');
            accent.className = 'onb-swatch-accent';
            accent.style.background = pair[1];
            tile.appendChild(primary);
            tile.appendChild(accent);
            tile.addEventListener('click', () => pick(i));
            tiles.push(tile);
            grid.appendChild(tile);
        }

        function pick(i) {
            selected = i;
            input.value = String(i);
            tiles.forEach((t, idx) => t.classList.toggle('on', idx === i));
            // Prévia ao vivo: aplica a cor na hora, sem esperar o submit.
            if (window.RastroosApplyPalette) window.RastroosApplyPalette(i);
        }
        pick(selected);
    }

    // ── Tema: prévia ao vivo no <body> ───────────────────────────────────
    function wireTheme(root) {
        root.querySelectorAll('[data-onb-theme]').forEach((radio) => {
            const sync = () => {
                root.querySelectorAll('[data-onb-theme-opt]').forEach((opt) => {
                    const r = opt.querySelector('[data-onb-theme]');
                    opt.classList.toggle('on', !!(r && r.checked));
                });
            };
            radio.addEventListener('change', () => {
                if (radio.checked && window.RastroosTheme) {
                    window.RastroosTheme.setTheme(radio.value);
                }
                sync();
            });
            sync();
        });
    }

    // ── Injeção + religação dos handlers a cada troca de passo ───────────
    function mount(node, first) {
        if (first) {
            window.RastroosModal.openNode(node, { wide: true, dismissible: false });
        } else {
            const body = document.querySelector('[data-modal-container] .modal-body');
            if (!body) return;
            body.innerHTML = '';
            body.appendChild(node);
        }
        const root = document.querySelector('[data-modal-container]');
        wire(root);
    }

    function wire(root) {
        root.querySelectorAll('[data-onb-step]').forEach((link) => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                go(link.getAttribute('href'));
            });
        });
        root.querySelectorAll('form').forEach((form) => {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                send(form);
            });
        });
        if (window.RastroosModal) window.RastroosModal.paintBindings(root);
        if (window.RastroosForms) window.RastroosForms.init(root);
        wirePalette(root);
        wireTheme(root);
        const firstField = root.querySelector('input:not([type=hidden]):not([readonly]), select');
        if (firstField) firstField.focus();
    }

    async function go(url) {
        try {
            const resp = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
            const doc = parse(await resp.text());
            const content = doc.querySelector(CONTENT);
            if (!content) { window.location.href = url; return; }
            window.RastroosModal.ensureStyles(doc);
            mount(document.importNode(content, true), false);
        } catch (err) {
            window.location.href = url;
        }
    }

    async function send(form) {
        try {
            const resp = await fetch(form.action, {
                method: 'POST',
                body: new FormData(form),
                headers: { 'X-Requested-With': 'fetch' },
            });
            // Redirecionou para fora do wizard = concluiu/pulou.
            if (!isOnboardingUrl(resp.url)) { finish(); return; }
            const doc = parse(await resp.text());
            const content = doc.querySelector(CONTENT);
            if (!content) { finish(); return; }
            window.RastroosModal.ensureStyles(doc);
            mount(document.importNode(content, true), false);
        } catch (err) {
            form.submit(); // rede falhou → deixa navegar
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const trigger = document.querySelector('[data-onboarding-trigger]');
        if (!trigger || !window.RastroosModal) return;
        const url = trigger.getAttribute('data-url') || ONB_PATH;
        try {
            const resp = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
            const doc = parse(await resp.text());
            const content = doc.querySelector(CONTENT);
            if (!content) return;
            window.RastroosModal.ensureStyles(doc);
            mount(document.importNode(content, true), true);
        } catch (err) {
            // Falhou o fetch: o wizard continua acessível na próxima navegação.
        }
    });
})();
