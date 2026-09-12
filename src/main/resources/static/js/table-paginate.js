/* ─────────────────────────────────────────────────────────────
   Rastroo$ — paginação client-side de tabelas
   ─────────────────────────────────────────────────────────────
   Uma <table data-paginate="10"> passa a mostrar 10 linhas por vez,
   com um rodapé "← Anterior · Página X de Y · Próxima →".

   É client-side de propósito: as tabelas que usam isso (histórico de
   login) vivem dentro de um modal carregado por fetch, onde um link
   de paginação server-side navegaria a página inteira e fecharia o
   modal. Os dados já vêm limitados pelo service (top 50), então não
   há custo em paginar no cliente.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const PAGINATED = 'data-paginate';

    const buildNav = (onGo) => {
        const nav = document.createElement('nav');
        nav.className = 'table-pagination';
        nav.setAttribute('aria-label', 'Paginação');

        const prev = document.createElement('button');
        prev.type = 'button';
        prev.className = 'btn-link';
        prev.textContent = '← Anterior';
        prev.addEventListener('click', () => onGo(-1));

        const info = document.createElement('span');
        info.className = 'table-pagination-info';

        const next = document.createElement('button');
        next.type = 'button';
        next.className = 'btn-link';
        next.textContent = 'Próxima →';
        next.addEventListener('click', () => onGo(1));

        nav.appendChild(prev);
        nav.appendChild(info);
        nav.appendChild(next);
        return { nav, prev, info, next };
    };

    const paginate = (table) => {
        if (table.dataset.paginateReady === '1') return;

        const perPage = parseInt(table.getAttribute(PAGINATED), 10);
        const body = table.tBodies[0];
        if (!body || !isFinite(perPage) || perPage < 1) return;

        const rows = Array.from(body.rows);
        const pages = Math.ceil(rows.length / perPage);
        table.dataset.paginateReady = '1';
        if (pages <= 1) return;

        let page = 0;
        const { nav, prev, info, next } = buildNav((delta) => {
            page = Math.min(pages - 1, Math.max(0, page + delta));
            render();
        });

        const render = () => {
            const from = page * perPage;
            rows.forEach((row, i) => { row.hidden = i < from || i >= from + perPage; });
            info.textContent = 'Página ' + (page + 1) + ' de ' + pages;
            prev.disabled = page === 0;
            next.disabled = page === pages - 1;
        };

        // O rodapé mora fora do .table-scroll, senão rolaria junto com a tabela.
        const scroll = table.closest('.table-scroll');
        (scroll || table).after(nav);
        render();
    };

    const scan = (root) => {
        (root || document).querySelectorAll('table[' + PAGINATED + ']').forEach(paginate);
    };

    document.addEventListener('DOMContentLoaded', () => scan(document));
    // Conteúdo aberto num modal (fetch) não passa pelo DOMContentLoaded.
    document.addEventListener('rastroos:modal-opened', (e) => {
        scan(e.detail && e.detail.container ? e.detail.container : document);
    });
})();
