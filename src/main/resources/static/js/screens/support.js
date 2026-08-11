// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/support
//   - Confirm para forms com data-confirm (cancelar chamado).
//   - Busca com debounce (submete o form da busca).
//   - Linha da tabela clicável → abre o chamado.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                var message = form.getAttribute("data-confirm") || "Confirmar?";
                if (!window.confirm(message)) event.preventDefault();
            });
        });

        // Busca com debounce — submete o próprio form da busca.
        var search = document.querySelector(".support-toolbar input[name='q']");
        if (search) {
            var form = search.closest("form");
            var debounced;
            search.addEventListener("input", function () {
                clearTimeout(debounced);
                debounced = setTimeout(function () { if (form) form.submit(); }, 350);
            });
        }

        // Linha clicável → abre o chamado (ignora cliques em links/botões).
        document.querySelectorAll("tr.support-row[data-row-href]").forEach(function (row) {
            row.addEventListener("click", function (e) {
                if (e.target.closest("a, button, form")) return;
                var href = row.getAttribute("data-row-href");
                if (href) window.location.href = href;
            });
        });
    });
})();
