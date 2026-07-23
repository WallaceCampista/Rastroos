/* ─────────────────────────────────────────────────────────────
   Rastro$ — /app/investments
   Clique num cofrinho/ativo → busca as movimentações e abre o
   detalhe abaixo (equivale ao InvestmentDetailModal do protótipo).
   Clicar de novo fecha; ignora cliques nas ações internas.
   ───────────────────────────────────────────────────────────── */
(function () {
    "use strict";

    var panel = document.querySelector("[data-invest-detail]");
    if (!panel) return;
    var openUrl = null;

    function paint(root) {
        root.querySelectorAll("[data-fill]").forEach(function (el) {
            el.style.background = el.getAttribute("data-fill");
        });
    }
    function markOpen(card) {
        document.querySelectorAll("[data-invest-detail-url].is-open").forEach(function (c) {
            c.classList.remove("is-open");
        });
        if (card) card.classList.add("is-open");
    }
    function close() {
        panel.hidden = true; panel.innerHTML = ""; openUrl = null; markOpen(null);
    }

    async function open(card) {
        var url = card.getAttribute("data-invest-detail-url");
        if (!url) return;
        if (openUrl === url) { close(); return; }
        try {
            var resp = await fetch(url, { headers: { "X-Requested-With": "fetch" } });
            var doc = new DOMParser().parseFromString(await resp.text(), "text/html");
            var content = doc.querySelector(".invest-detail");
            if (!content) { window.location.href = url; return; }
            panel.innerHTML = "";
            panel.appendChild(document.importNode(content, true));
            paint(panel);
            panel.hidden = false;
            openUrl = url;
            markOpen(card);
            panel.querySelectorAll("[data-invest-detail-close]").forEach(function (b) {
                b.addEventListener("click", close);
            });
            panel.querySelectorAll("form[data-confirm]").forEach(function (f) {
                f.addEventListener("submit", function (e) {
                    if (!window.confirm(f.getAttribute("data-confirm") || "Confirmar?")) e.preventDefault();
                });
            });
            panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
        } catch (e) {
            window.location.href = url;
        }
    }

    document.querySelectorAll("[data-invest-detail-url]").forEach(function (card) {
        card.addEventListener("click", function (e) {
            // ignora cliques em ações internas (editar/remover na carteira)
            if (e.target.closest("a, button:not([data-invest-detail-url]), form")) return;
            open(card);
        });
    });
})();
