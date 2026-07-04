/* ─────────────────────────────────────────────────────────────
   Rastro$ — boot do app autenticado
   ─────────────────────────────────────────────────────────────
   Responsável por:
   • montar dropdown do user-menu
   • toggle global "ocultar valores"
   • controles do period selector (mês/ano)
   Cada tela das Etapas 7+ adiciona seus próprios módulos em
   /js/screens/*.js, carregados sob demanda.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    // ── User menu dropdown ────────────────────────────────────
    const trigger  = document.querySelector('[data-user-menu-trigger]');
    const dropdown = document.querySelector('[data-user-menu-dropdown]');

    if (trigger && dropdown) {
        const closeMenu = () => { dropdown.hidden = true; trigger.setAttribute('aria-expanded', 'false'); };
        const openMenu  = () => { dropdown.hidden = false; trigger.setAttribute('aria-expanded', 'true'); };

        trigger.setAttribute('aria-expanded', 'false');
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            dropdown.hidden ? openMenu() : closeMenu();
        });
        document.addEventListener('click', (e) => {
            if (!dropdown.hidden && !dropdown.contains(e.target)) closeMenu();
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !dropdown.hidden) closeMenu();
        });
    }

    // ── Ocultar valores global ────────────────────────────────
    const hideToggle = document.querySelector('[data-toggle-hide-values]');
    if (hideToggle) {
        const KEY = 'rastroos.hideValues';
        const apply = (hidden) => {
            document.body.classList.toggle('values-hidden', hidden);
            hideToggle.setAttribute('aria-pressed', String(hidden));
        };
        apply(localStorage.getItem(KEY) === 'true');
        hideToggle.addEventListener('click', () => {
            const next = !document.body.classList.contains('values-hidden');
            localStorage.setItem(KEY, String(next));
            apply(next);
        });
    }

    // ── Period selector (prev/next mês) ───────────────────────
    const prev = document.querySelector('[data-period-prev]');
    const next = document.querySelector('[data-period-next]');
    const navigatePeriod = (delta) => {
        const url = new URL(window.location.href);
        const ym  = url.searchParams.get('ym') || new Date().toISOString().slice(0, 7);
        const [y, m] = ym.split('-').map(Number);
        const d = new Date(Date.UTC(y, m - 1 + delta, 1));
        const nextYm = d.toISOString().slice(0, 7);
        url.searchParams.set('ym', nextYm);
        window.location.href = url.toString();
    };
    prev?.addEventListener('click', () => navigatePeriod(-1));
    next?.addEventListener('click', () => navigatePeriod(+1));
})();
