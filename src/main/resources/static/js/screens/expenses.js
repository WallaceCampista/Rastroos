// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/expenses
//   - Confirmação inline antes de submeter forms com data-confirm.
//   - Auto-submit do filtro ao mudar selects (UX).
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                var message = form.getAttribute("data-confirm") || "Confirmar?";
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });

        var filterBar = document.querySelector(".filter-bar");
        if (!filterBar) return;
        var debounced;
        filterBar.querySelectorAll("select").forEach(function (sel) {
            sel.addEventListener("change", function () {
                filterBar.submit();
            });
        });
        var search = filterBar.querySelector("input[name='q']");
        if (search) {
            search.addEventListener("input", function () {
                clearTimeout(debounced);
                debounced = setTimeout(function () {
                    filterBar.submit();
                }, 350);
            });
        }
    });
})();
