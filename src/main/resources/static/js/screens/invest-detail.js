/* ─────────────────────────────────────────────────────────────
   Rastro$ — /app/investments
   Clique num cofrinho/ativo → busca as movimentações e abre o
   detalhe num MODAL central (equivale ao InvestmentDetailModal do
   protótipo). Ignora cliques nas ações internas.
   ───────────────────────────────────────────────────────────── */
(function () {
    "use strict";

    async function open(url) {
        if (!url) return;
        if (!window.RastroosModal) { window.location.href = url; return; }
        try {
            var resp = await fetch(url, { headers: { "X-Requested-With": "fetch" } });
            var doc = new DOMParser().parseFromString(await resp.text(), "text/html");
            var content = doc.querySelector(".invest-detail");
            if (!content) { window.location.href = url; return; }
            window.RastroosModal.openNode(document.importNode(content, true), { wide: true });
        } catch (e) {
            window.location.href = url;
        }
    }

    document.querySelectorAll("[data-invest-detail-url]").forEach(function (card) {
        card.addEventListener("click", function (e) {
            // ignora cliques em ações internas (editar/remover na carteira)
            if (e.target.closest("a, button:not([data-invest-detail-url]), form")) return;
            open(card.getAttribute("data-invest-detail-url"));
        });
    });
})();
