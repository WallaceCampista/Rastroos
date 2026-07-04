/* ─────────────────────────────────────────────────────────────
   Rastro$ — tema (claro/escuro), paleta e densidade
   ─────────────────────────────────────────────────────────────
   O servidor define os defaults via data-theme/density/palette
   no <body>. Aqui aplicamos overrides locais (localStorage) e
   expomos ganchos para o painel de preferências (Etapa 14).
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const body = document.body;
    const THEME_KEY   = 'rastroos.theme';
    const DENSITY_KEY = 'rastroos.density';
    const PALETTE_KEY = 'rastroos.palette';

    const setAttr = (name, value) => {
        if (value === null || value === undefined) return;
        body.setAttribute('data-' + name, String(value));
    };

    const stored = {
        theme:   localStorage.getItem(THEME_KEY),
        density: localStorage.getItem(DENSITY_KEY),
        palette: localStorage.getItem(PALETTE_KEY),
    };
    setAttr('theme',   stored.theme);
    setAttr('density', stored.density);
    setAttr('palette', stored.palette);

    // API pública mínima para a Etapa 14 (preferências do usuário).
    window.RastroosTheme = {
        setTheme(theme) {
            localStorage.setItem(THEME_KEY, theme);
            setAttr('theme', theme);
        },
        setDensity(density) {
            localStorage.setItem(DENSITY_KEY, density);
            setAttr('density', density);
        },
        setPalette(index) {
            localStorage.setItem(PALETTE_KEY, String(index));
            setAttr('palette', String(index));
        },
        current() {
            return {
                theme:   body.dataset.theme,
                density: body.dataset.density,
                palette: body.dataset.palette,
            };
        },
    };
})();
