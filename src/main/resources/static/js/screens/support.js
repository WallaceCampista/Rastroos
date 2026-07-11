// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/support
//   - Confirm para forms com data-confirm (cancelar chamado).
//   - Auto-submit do filtro (select onChange + debounce na busca).
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
            sel.addEventListener("change", function () { filterBar.submit(); });
        });
        var search = filterBar.querySelector("input[name='q']");
        if (search) {
            search.addEventListener("input", function () {
                clearTimeout(debounced);
                debounced = setTimeout(function () { filterBar.submit(); }, 350);
            });
        }
    });
})();
