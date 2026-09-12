/* ─────────────────────────────────────────────────────────────
   Rastroo$ — Markdown mínimo das respostas do Alfredo
   ─────────────────────────────────────────────────────────────
   O modelo responde em Markdown (negrito, listas, títulos). Sem
   interpretar, a tela mostrava "**não é recomendado**" com os
   asteriscos e uma lista numerada virava um parágrafo só.

   Constrói DOM com createElement/textContent — NUNCA innerHTML.
   O texto vem de um modelo alimentado por dados do usuário, ou
   seja, é conteúdo não confiável (§3.2): montando nó a nó não há
   como um `<img onerror=...>` virar elemento, e a CSP estrita
   continua valendo sem exceção nenhuma.

   Subconjunto suportado (é o que o Alfredo usa de verdade):
     **negrito**  *itálico*  _itálico_  `código`
     - lista        * lista       1. lista numerada
     ### título     parágrafos separados por linha em branco
   O que não estiver aqui aparece como texto literal, que é o
   pior caso aceitável: nunca some conteúdo.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const BULLET = /^\s*[-*+]\s+(.*)$/;
    const ORDERED = /^\s*(\d{1,3})[.)]\s+(.*)$/;
    const HEADING = /^\s*(#{1,6})\s+(.*)$/;

    /**
     * Trechos inline de uma linha, na ordem em que aparecem.
     * Varre uma vez procurando o próximo marcador; o que sobra é texto.
     */
    function inlineNodes(line) {
        const out = [];
        let rest = line;

        // Ordem importa: ** antes de * para "**a**" não virar itálico vazio.
        const rules = [
            { re: /\*\*([^*]+)\*\*/, tag: 'strong' },
            { re: /__([^_]+)__/,     tag: 'strong' },
            { re: /`([^`]+)`/,       tag: 'code' },
            { re: /\*([^*\n]+)\*/,   tag: 'em' },
            { re: /(?:^|(?<=\s))_([^_\n]+)_(?=\s|$|[.,;:!?)])/, tag: 'em' },
        ];

        let guard = 0;
        while (rest && guard++ < 500) {
            let best = null;
            for (const rule of rules) {
                const m = rule.re.exec(rest);
                if (m && (best === null || m.index < best.match.index)) {
                    best = { match: m, tag: rule.tag };
                }
            }
            if (!best) break;

            const before = rest.slice(0, best.match.index);
            if (before) out.push(document.createTextNode(before));

            const el = document.createElement(best.tag);
            el.textContent = best.match[1];
            out.push(el);

            rest = rest.slice(best.match.index + best.match[0].length);
        }
        if (rest) out.push(document.createTextNode(rest));
        return out;
    }

    /** Preenche `parent` com o inline de `line`. */
    function fillInline(parent, line) {
        inlineNodes(line).forEach((n) => parent.appendChild(n));
    }

    /** Um bloco (parágrafo, lista ou título) vira um elemento. */
    function blockElement(lines) {
        const first = lines[0];

        const heading = HEADING.exec(first);
        if (heading) {
            // Rebaixa o nível: dentro de um balão de chat um <h1> destoa.
            const level = Math.min(6, heading[1].length + 3);
            const el = document.createElement('h' + level);
            el.className = 'md-h';
            fillInline(el, heading[2]);
            return el;
        }

        if (lines.every((l) => BULLET.test(l))) {
            const ul = document.createElement('ul');
            ul.className = 'md-list';
            lines.forEach((l) => {
                const li = document.createElement('li');
                fillInline(li, BULLET.exec(l)[1]);
                ul.appendChild(li);
            });
            return ul;
        }

        if (lines.every((l) => ORDERED.test(l))) {
            const ol = document.createElement('ol');
            ol.className = 'md-list';
            // Respeita a numeração do modelo (pode começar em 2 numa continuação).
            const start = parseInt(ORDERED.exec(first)[1], 10);
            if (start > 1) ol.start = start;
            lines.forEach((l) => {
                const li = document.createElement('li');
                fillInline(li, ORDERED.exec(l)[2]);
                ol.appendChild(li);
            });
            return ol;
        }

        const p = document.createElement('p');
        p.className = 'md-p';
        lines.forEach((line, i) => {
            if (i > 0) p.appendChild(document.createElement('br'));
            fillInline(p, line);
        });
        return p;
    }

    /**
     * Agrupa em blocos: linha em branco separa, e um item de lista começa
     * bloco novo quando o anterior não era lista (o modelo nem sempre deixa
     * linha em branco antes da lista).
     */
    function blocks(text) {
        const out = [];
        let current = [];
        const isItem = (l) => BULLET.test(l) || ORDERED.test(l);

        const flush = () => {
            if (current.length) out.push(current);
            current = [];
        };

        for (const raw of String(text).replace(/\r\n?/g, '\n').split('\n')) {
            const line = raw.replace(/\s+$/, '');
            if (!line.trim()) { flush(); continue; }
            if (current.length && isItem(line) !== isItem(current[0])) flush();
            if (current.length && HEADING.test(line)) flush();
            current.push(line);
        }
        flush();
        return out;
    }

    /** Substitui o conteúdo de `el` pelo Markdown de `text` renderizado. */
    function render(el, text) {
        el.textContent = '';
        const value = text == null ? '' : String(text);
        if (!value.trim()) return;
        for (const lines of blocks(value)) {
            el.appendChild(blockElement(lines));
        }
    }

    /**
     * Reaproveita o texto que o servidor já escreveu no elemento (escapado
     * por th:text) e o promove a Markdown. Sem JS o usuário vê o texto cru —
     * feio, mas legível e completo.
     */
    function upgrade(root) {
        (root || document).querySelectorAll('[data-md]:not([data-md-done])')
            .forEach((el) => {
                el.setAttribute('data-md-done', '1');
                render(el, el.textContent);
            });
    }

    window.RastroosMarkdown = { render: render, upgrade: upgrade };
    document.addEventListener('DOMContentLoaded', () => upgrade(document));
})();
