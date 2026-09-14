/* ─────────────────────────────────────────────────────────────
   Rastroo$ — comemoração ao quitar
   ─────────────────────────────────────────────────────────────
   "Marque como pago e veja seus emojis subindo na tela" — a promessa
   da landing. Marcar como pago é um POST que redireciona, então a
   animação não pode ser disparada no clique (a página recarrega em
   seguida e mataria tudo). Ela é disparada na página JÁ recarregada,
   a partir da CHAVE do flash que o toast.js repassa.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    // Chaves que merecem festa: quitar um gasto e fechar uma fatura.
    const FESTEJA = {
        'transaction.markedPaid': ['🎉', '💸', '🤑', '👍', '✅', '🥳'],
        'account.invoicePaid': ['🎉', '💳', '🤑', '👍', '✅'],
    };

    const QUANTIDADE = 14;

    const aleatorio = (min, max) => min + Math.random() * (max - min);

    const celebrar = (emojis, quantidade) => {
        // Respeita quem pediu menos movimento no sistema.
        if (window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
        }

        const camada = document.createElement('div');
        camada.className = 'celebrate-layer';
        camada.setAttribute('aria-hidden', 'true');

        let maisLongo = 0;
        for (let i = 0; i < (quantidade || QUANTIDADE); i++) {
            const span = document.createElement('span');
            span.className = 'celebrate-emoji';
            span.textContent = emojis[Math.floor(Math.random() * emojis.length)];

            const duracao = aleatorio(1700, 2600);
            const atraso = aleatorio(0, 500);
            maisLongo = Math.max(maisLongo, duracao + atraso);

            // Variação por partícula via CSSOM — nada de style inline no HTML.
            span.style.setProperty('--cl-left', aleatorio(6, 94).toFixed(2) + 'vw');
            span.style.setProperty('--cl-drift', aleatorio(-70, 70).toFixed(0) + 'px');
            span.style.setProperty('--cl-rot', aleatorio(-40, 40).toFixed(0) + 'deg');
            span.style.setProperty('--cl-scale', aleatorio(0.75, 1.4).toFixed(2));
            span.style.setProperty('--cl-duration', duracao.toFixed(0) + 'ms');
            span.style.setProperty('--cl-delay', atraso.toFixed(0) + 'ms');

            camada.appendChild(span);
        }

        // Modal aberto: os emojis sobem por cima do desfoque e por trás da caixa
        // (a regra .modal-backdrop > .celebrate-layer cuida da ordem).
        const modal = document.querySelector('[data-modal-backdrop]:not([hidden])');
        (modal || document.body).appendChild(camada);
        // Some sozinha: a camada é decorativa e não pode ficar sobre a tela.
        window.setTimeout(() => camada.remove(), maisLongo + 300);
    };

    document.addEventListener('rastroos:flash', (e) => {
        const detail = e.detail || {};
        // O detalhe da conta comemora com regra própria (pela situação da conta)
        // e avisa aqui para a festa genérica não sair junto.
        if (detail.celebrated) return;
        if (detail.key && FESTEJA[detail.key]) celebrar(FESTEJA[detail.key]);
    });

    window.RastroosCelebrate = { celebrate: celebrar };
})();
