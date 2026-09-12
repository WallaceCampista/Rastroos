/* ─────────────────────────────────────────────────────────────
   Rastroo$ — paletas de cor (--primary / --accent)
   ─────────────────────────────────────────────────────────────
   Vive num arquivo próprio porque três lugares precisam da MESMA
   lista: o menu do usuário (app.js), o wizard de boas-vindas
   (onboarding.js) e o índice guardado em users.palette_index.
   A ordem é o contrato — mexer nela remapeia a escolha de quem
   já configurou. Para acrescentar uma paleta, adicione no FIM
   (e suba OnboardingService.PALETTE_COUNT).
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    window.RastroosPalettes = [
        ['#6366f1', '#fbbf24'], ['#22c55e', '#f5f5f5'], ['#d97757', '#1a1a1a'],
        ['#0f766e', '#fb7185'], ['#fde047', '#a78bfa'], ['#ec4899', '#22d3ee'],
        ['#0ea5e9', '#f97316'], ['#7c3aed', '#10b981'], ['#dc2626', '#fcd34d'],
        ['#1e293b', '#facc15'], ['#14b8a6', '#f43f5e'], ['#84cc16', '#8b5cf6'],
        ['#1e40af', '#fb7185'], ['#9333ea', '#fde047'], ['#06b6d4', '#f472b6'],
        ['#16a34a', '#fbbf24'], ['#ea580c', '#0ea5e9'], ['#be123c', '#a3e635'],
    ];

    /** Aplica a paleta às CSS vars e avisa quem desenha em canvas. */
    window.RastroosApplyPalette = (index) => {
        const p = window.RastroosPalettes[index] || window.RastroosPalettes[0];
        document.documentElement.style.setProperty('--primary', p[0]);
        document.documentElement.style.setProperty('--accent', p[1]);
        window.dispatchEvent(new CustomEvent('rastroos:themechange'));
        return p;
    };
})();
