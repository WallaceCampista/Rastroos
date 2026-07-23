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
        const closeMenu = () => { dropdown.hidden = true; trigger.classList.remove('is-open'); trigger.setAttribute('aria-expanded', 'false'); };
        const openMenu  = () => { dropdown.hidden = false; trigger.classList.add('is-open'); trigger.setAttribute('aria-expanded', 'true'); };

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

    // ── Menu do usuário: idioma / modo escuro / paletas ───────
    const PALETTES = [
        ['#6366f1', '#fbbf24'], ['#22c55e', '#f5f5f5'], ['#d97757', '#1a1a1a'],
        ['#0f766e', '#fb7185'], ['#fde047', '#a78bfa'], ['#ec4899', '#22d3ee'],
        ['#0ea5e9', '#f97316'], ['#7c3aed', '#10b981'], ['#dc2626', '#fcd34d'],
        ['#1e293b', '#facc15'], ['#14b8a6', '#f43f5e'], ['#84cc16', '#8b5cf6'],
        ['#1e40af', '#fb7185'], ['#9333ea', '#fde047'], ['#06b6d4', '#f472b6'],
        ['#16a34a', '#fbbf24'], ['#ea580c', '#0ea5e9'], ['#be123c', '#a3e635'],
    ];
    const docRoot = document.documentElement;
    const currentPaletteIndex = () => {
        const i = parseInt(document.body.dataset.palette, 10);
        return (!Number.isNaN(i) && PALETTES[i]) ? i : 0;
    };
    const applyPalette = (i) => {
        const p = PALETTES[i] || PALETTES[0];
        docRoot.style.setProperty('--primary', p[0]);
        docRoot.style.setProperty('--accent', p[1]);
        // Avisa quem desenha em canvas (gráficos) para redesenhar com a
        // nova cor — CSS reage sozinho via var(--primary), canvas não.
        window.dispatchEvent(new CustomEvent('rastroos:themechange'));
    };
    applyPalette(currentPaletteIndex());

    const colorPreview = document.querySelector('[data-color-preview]');
    const paintPreview = (p) => {
        if (!colorPreview) return;
        const spans = colorPreview.querySelectorAll('span');
        if (spans[0]) spans[0].style.background = p[0];
        if (spans[1]) spans[1].style.background = p[1];
    };
    paintPreview(PALETTES[currentPaletteIndex()]);

    // Idioma → recarrega a página atual com ?lang=
    document.querySelectorAll('[data-lang]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const url = new URL(window.location.href);
            url.searchParams.set('lang', btn.getAttribute('data-lang'));
            window.location.href = url.toString();
        });
    });

    // Modo escuro/claro
    const themeToggle = document.querySelector('[data-theme-toggle]');
    if (themeToggle && window.RastroosTheme) {
        const themeIcon = document.querySelector('[data-theme-icon]');
        const themeLabel = document.querySelector('[data-theme-label]');
        const MOON = '<path d="M21 12.79A9 9 0 1 1 11.21 3a7 7 0 0 0 9.79 9.79z"/>';
        const SUN = '<path d="M12 3v2m0 14v2m9-9h-2M5 12H3m15.36-6.36-1.4 1.4M7.04 16.96l-1.4 1.4m12.72 0-1.4-1.4M7.04 7.04l-1.4-1.4M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10z"/>';
        const syncTheme = () => {
            const dark = (document.body.dataset.theme || 'dark') !== 'light';
            themeToggle.classList.toggle('on', dark);
            themeToggle.setAttribute('aria-checked', String(dark));
            if (themeIcon) themeIcon.innerHTML = dark ? MOON : SUN;
            if (themeLabel) themeLabel.textContent = dark ? 'Modo escuro' : 'Modo claro';
        };
        syncTheme();
        themeToggle.addEventListener('click', () => {
            const dark = (document.body.dataset.theme || 'dark') !== 'light';
            window.RastroosTheme.setTheme(dark ? 'light' : 'dark');
            syncTheme();
        });
    }

    // Tema de cores: expandir grid de paletas
    const themeExpand = document.querySelector('[data-theme-expand]');
    const themeTiles = document.querySelector('[data-theme-tiles]');
    if (themeExpand && themeTiles) {
        const expandChev = themeExpand.querySelector('.um-action-chev-toggle');
        const activeIdx = currentPaletteIndex();
        PALETTES.forEach((p, i) => {
            const tile = document.createElement('button');
            tile.type = 'button';
            tile.className = 'um-theme-tile' + (i === activeIdx ? ' on' : '');
            tile.setAttribute('aria-label', 'Paleta ' + (i + 1));
            const primary = document.createElement('span');
            primary.className = 'um-theme-tile-primary';
            primary.style.background = p[0];
            const accent = document.createElement('span');
            accent.className = 'um-theme-tile-accent';
            accent.style.background = p[1];
            tile.appendChild(primary);
            tile.appendChild(accent);
            tile.addEventListener('click', () => {
                applyPalette(i);
                if (window.RastroosTheme) window.RastroosTheme.setPalette(i);
                themeTiles.querySelectorAll('.um-theme-tile').forEach((t) => t.classList.remove('on'));
                tile.classList.add('on');
                paintPreview(p);
            });
            themeTiles.appendChild(tile);
        });
        themeExpand.addEventListener('click', () => {
            const willOpen = themeTiles.hasAttribute('hidden');
            themeTiles.toggleAttribute('hidden', !willOpen);
            themeExpand.classList.toggle('is-expanded', willOpen);
            themeExpand.setAttribute('aria-expanded', String(willOpen));
            if (expandChev) expandChev.classList.toggle('is-open', willOpen);
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

    // ── Seletor de período: indicador que desliza do mês anterior para o novo ──
    // Como a navegação recarrega a página, guardamos o índice do mês ativo em
    // sessionStorage e animamos o indicador da posição antiga → nova (efeito SPA).
    const psTrack = document.querySelector('[data-ps-track]');
    if (psTrack) {
        const psChips = Array.prototype.slice.call(psTrack.querySelectorAll('.ps-chip'));
        const psActive = psTrack.querySelector('.ps-chip.on');
        const psIndicator = psTrack.querySelector('[data-ps-indicator]');
        if (psActive && psIndicator) {
            const PREV_KEY = 'rastroos.psPrevIndex';
            const activeIndex = psChips.indexOf(psActive);
            const prevIndex = parseInt(sessionStorage.getItem(PREV_KEY), 10);
            const fromChip = (!Number.isNaN(prevIndex) && psChips[prevIndex]) ? psChips[prevIndex] : null;

            // centraliza o mês ativo no trilho
            psTrack.scrollLeft = psActive.offsetLeft - (psTrack.clientWidth - psActive.clientWidth) / 2;

            const place = (chip, animated) => {
                psIndicator.style.transition = animated ? '' : 'none';
                psIndicator.style.width = chip.offsetWidth + 'px';
                psIndicator.style.transform = 'translateX(' + chip.offsetLeft + 'px)';
                psIndicator.style.opacity = '1';
            };

            if (fromChip && fromChip !== psActive) {
                place(fromChip, false);          // posiciona no mês anterior, sem animar
                void psIndicator.offsetWidth;    // força reflow
                requestAnimationFrame(() => place(psActive, true)); // desliza até o ativo
            } else {
                // Mesma seleção (troca de página, não de mês): posiciona
                // instantâneo — o indicador não deve "escorregar" a cada navegação.
                place(psActive, false);
            }
            sessionStorage.setItem(PREV_KEY, String(activeIndex));
        }
    }

    // ── Sidebar colapsável (burger) ──────────────────────────
    const appShell    = document.querySelector('[data-app-shell]');
    const sidebarEl   = document.querySelector('.sidebar');
    const sideToggle  = document.querySelector('[data-sidebar-toggle]');
    if (appShell && sidebarEl && sideToggle) {
        const COLLAPSE_KEY = 'rastroos.sideCollapsed';
        const applyCollapsed = (collapsed) => {
            appShell.classList.toggle('side-collapsed', collapsed);
            sidebarEl.classList.toggle('is-collapsed', collapsed);
            sideToggle.setAttribute('aria-label', collapsed ? 'Expandir menu' : 'Recolher menu');
        };
        applyCollapsed(localStorage.getItem(COLLAPSE_KEY) === 'true');
        sideToggle.addEventListener('click', () => {
            const next = !appShell.classList.contains('side-collapsed');
            localStorage.setItem(COLLAPSE_KEY, String(next));
            applyCollapsed(next);
        });
    }

    // ── Drawer mobile (abre/fecha a sidebar com scrim) ────────
    const sidebarWrap = document.querySelector('[data-sidebar-wrap]');
    const mobileToggle = document.querySelector('[data-mobile-nav-toggle]');
    if (sidebarWrap && mobileToggle) {
        let scrim = null;
        const closeDrawer = () => {
            sidebarWrap.classList.remove('open');
            scrim?.remove();
            scrim = null;
        };
        const openDrawer = () => {
            sidebarWrap.classList.add('open');
            scrim = document.createElement('div');
            scrim.className = 'nav-scrim';
            scrim.addEventListener('click', closeDrawer);
            document.body.appendChild(scrim);
        };
        mobileToggle.addEventListener('click', () => {
            sidebarWrap.classList.contains('open') ? closeDrawer() : openDrawer();
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && sidebarWrap.classList.contains('open')) closeDrawer();
        });
    }

    // ── Form de usuário (admin): alvo só aparece quando papel = Acessor ──
    // Puramente cosmético — o service ignora o alvo se o papel não for ACESSOR.
    const roleSelect  = document.querySelector('[data-role-select]');
    const targetField = document.querySelector('[data-accessor-target-field]');
    if (roleSelect && targetField) {
        const apply = () => { targetField.hidden = roleSelect.value !== 'ACESSOR'; };
        apply();
        roleSelect.addEventListener('change', apply);
    }
})();
