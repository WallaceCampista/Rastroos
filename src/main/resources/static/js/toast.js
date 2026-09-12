/* ─────────────────────────────────────────────────────────────
   Rastroo$ — toasts
   ─────────────────────────────────────────────────────────────
   As telas renderizam a mensagem do servidor como <div class="flash">
   no topo do conteúdo. Isso empurra a página e a mensagem fica lá
   para sempre ("Status do usuário atualizado." grudado na tela).

   Aqui essa div é promovida a toast flutuante com uma linha de
   tempo que esvazia durante a exibição. A linha É o cronômetro: o
   fim da animação dispara o fechamento, então passar o mouse
   (animation-play-state: paused) congela barra e contagem juntas,
   sem os dois saírem de sincronia.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const DURATION = { ok: 5000, error: 8000 };
    const ICON = {
        ok: 'M20 6L9 17l-5-5',
        error: 'M12 8v5m0 3.5v.01M10.3 3.9L2.4 17.3A1.9 1.9 0 0 0 4 20.2h16a1.9 1.9'
             + ' 0 0 0 1.6-2.9L13.7 3.9a1.9 1.9 0 0 0-3.4 0z',
    };

    let stack = null;

    const getStack = () => {
        if (!stack) {
            stack = document.createElement('div');
            stack.className = 'toast-stack';
            stack.setAttribute('aria-live', 'polite');
            document.body.appendChild(stack);
        }
        return stack;
    };

    const svg = (path) => {
        const ns = 'http://www.w3.org/2000/svg';
        const el = document.createElementNS(ns, 'svg');
        el.setAttribute('viewBox', '0 0 24 24');
        el.setAttribute('fill', 'none');
        el.setAttribute('stroke', 'currentColor');
        el.setAttribute('stroke-width', '2.2');
        el.setAttribute('stroke-linecap', 'round');
        el.setAttribute('stroke-linejoin', 'round');
        el.setAttribute('aria-hidden', 'true');
        const p = document.createElementNS(ns, 'path');
        p.setAttribute('d', path);
        el.appendChild(p);
        return el;
    };

    const show = (text, kind) => {
        if (!text) return;
        const duration = DURATION[kind] || DURATION.ok;

        const toast = document.createElement('div');
        toast.className = 'toast toast-' + kind;
        toast.setAttribute('role', kind === 'error' ? 'alert' : 'status');

        const icon = document.createElement('span');
        icon.className = 'toast-icon';
        icon.appendChild(svg(ICON[kind] || ICON.ok));

        const body = document.createElement('p');
        body.className = 'toast-text';
        body.textContent = text;

        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'toast-close';
        close.setAttribute('aria-label', 'Fechar');
        close.appendChild(svg('M6 6l12 12M6 18L18 6'));

        const bar = document.createElement('span');
        bar.className = 'toast-bar';
        const fill = document.createElement('span');
        fill.className = 'toast-bar-fill';
        // Duração via CSSOM (nada de style inline no HTML — CSP é 'self').
        fill.style.setProperty('--toast-duration', duration + 'ms');
        bar.appendChild(fill);

        toast.append(icon, body, close, bar);
        getStack().appendChild(toast);

        let done = false;
        const dismiss = () => {
            if (done) return;
            done = true;
            toast.classList.add('is-leaving');
            const gone = () => toast.remove();
            toast.addEventListener('animationend', (e) => {
                if (e.target === toast) gone();
            });
            window.setTimeout(gone, 400);
        };

        // Quem fecha o toast é um timer próprio, não o fim da animação: se a
        // animação não roda (aba em segundo plano, animações desligadas, um
        // headless), o animationend nunca chega e o toast ficaria preso — que é
        // exatamente o que o usuário reclamou da mensagem fixa. A barra é a
        // representação visual desse timer, e o hover pausa os dois juntos.
        let remaining = duration;
        let startedAt = 0;
        let timer = null;

        const resume = () => {
            startedAt = Date.now();
            timer = window.setTimeout(dismiss, remaining);
        };
        const pause = () => {
            window.clearTimeout(timer);
            remaining -= Date.now() - startedAt;
        };

        toast.addEventListener('mouseenter', pause);
        toast.addEventListener('mouseleave', resume);
        fill.addEventListener('animationend', dismiss); // o que vier primeiro
        close.addEventListener('click', dismiss);
        resume();
    };

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.flash').forEach((el) => {
            const text = el.textContent.trim();
            const kind = el.classList.contains('flash-error') ? 'error' : 'ok';
            const key = el.getAttribute('data-flash-key');
            el.remove(); // sai do fluxo: não empurra mais o conteúdo da tela
            show(text, kind);
            // A CHAVE (não o texto traduzido) segue para quem quiser reagir a
            // um resultado específico — o celebrate.js, por exemplo.
            document.dispatchEvent(new CustomEvent('rastroos:flash',
                { detail: { key: key, kind: kind, text: text } }));
        });
    });

    // Para outros scripts avisarem algo sem recarregar a página.
    window.RastroosToast = { show: show };
})();
