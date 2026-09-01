// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/cards
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

        // cor do gradiente de cada cartão (via CSSOM — compatível com a CSP)
        document.querySelectorAll(".card-tile-cartao[data-color]").forEach(function (tile) {
            tile.style.setProperty("--acc-c", tile.getAttribute("data-color"));
        });

        setupSort();
    });

    // Ordenação client-side dos cards por data de vencimento (data-due-day).
    function setupSort() {
        var select = document.querySelector("[data-cards-sort]");
        var grid = document.querySelector(".cards-grid");
        if (!select || !grid) return;

        var tiles = Array.prototype.slice.call(grid.querySelectorAll(".card-tile"));
        tiles.forEach(function (tile, i) { tile.dataset.origIndex = String(i); });

        function dueOf(tile) { return parseInt(tile.getAttribute("data-due-day"), 10) || 99; }
        function origOf(tile) { return parseInt(tile.dataset.origIndex, 10) || 0; }

        function apply() {
            var mode = select.value;
            var sorted = tiles.slice();
            if (mode === "due-asc") {
                sorted.sort(function (a, b) { return dueOf(a) - dueOf(b) || origOf(a) - origOf(b); });
            } else if (mode === "due-desc") {
                sorted.sort(function (a, b) { return dueOf(b) - dueOf(a) || origOf(a) - origOf(b); });
            } else {
                sorted.sort(function (a, b) { return origOf(a) - origOf(b); });
            }
            sorted.forEach(function (tile) { grid.appendChild(tile); });
        }

        select.addEventListener("change", apply);
    }
})();
