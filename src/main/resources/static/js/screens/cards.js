// ─────────────────────────────────────────────────────────────
// Rastroo$ — /app/cards
// Confirmação inline antes de submeter forms com data-confirm.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var forms = document.querySelectorAll("form[data-confirm]");
        forms.forEach(function (form) {
            form.addEventListener("submit", function (event) {
                var message = form.getAttribute("data-confirm") || "Confirmar?";
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });

        paintTiles(document);
        setupSort();
    });

    // cor do gradiente de cada cartão (via CSSOM — compatível com a CSP)
    function paintTiles(root) {
        root.querySelectorAll(".card-tile-cartao[data-color]").forEach(function (tile) {
            tile.style.setProperty("--acc-c", tile.getAttribute("data-color"));
        });
    }

    // Ordenação client-side dos cards por data de vencimento (data-due-day).
    // Os cards são relidos a cada ordenação: o cards-detail.js troca a grade
    // inteira depois de pagar uma fatura sem recarregar a página.
    function setupSort() {
        var select = document.querySelector("[data-cards-sort]");
        if (!select) return;

        function grid() { return document.querySelector(".cards-grid"); }

        function stampOrder() {
            var g = grid();
            if (!g) return;
            g.querySelectorAll(".card-tile").forEach(function (tile, i) { tile.dataset.origIndex = String(i); });
        }

        function dueOf(tile) { return parseInt(tile.getAttribute("data-due-day"), 10) || 99; }
        function origOf(tile) { return parseInt(tile.dataset.origIndex, 10) || 0; }

        function apply() {
            var g = grid();
            if (!g) return;
            var mode = select.value;
            var sorted = Array.prototype.slice.call(g.querySelectorAll(".card-tile"));
            if (mode === "due-asc") {
                sorted.sort(function (a, b) { return dueOf(a) - dueOf(b) || origOf(a) - origOf(b); });
            } else if (mode === "due-desc") {
                sorted.sort(function (a, b) { return dueOf(b) - dueOf(a) || origOf(a) - origOf(b); });
            } else {
                sorted.sort(function (a, b) { return origOf(a) - origOf(b); });
            }
            sorted.forEach(function (tile) { g.appendChild(tile); });
        }

        stampOrder();
        select.addEventListener("change", apply);

        // Grade nova veio do servidor na ordem padrão: carimba e reaplica a escolhida.
        document.addEventListener("rastroos:cards-refreshed", function () {
            var g = grid();
            if (!g) return;
            paintTiles(g);
            stampOrder();
            apply();
        });
    }
})();
