// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/cards/{new,edit}
// Esconde/mostra campos específicos de cada tipo de conta.
// Sem inline: lê data-only-kind e reage à mudança do <select>.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var select = document.querySelector("[data-account-kind]");
        if (!select) return;

        var conditionalFields = document.querySelectorAll("[data-only-kind]");

        function apply() {
            var current = select.value;
            conditionalFields.forEach(function (field) {
                var allowed = (field.getAttribute("data-only-kind") || "")
                    .split(",")
                    .map(function (k) { return k.trim(); });
                field.hidden = allowed.indexOf(current) === -1;
            });
        }

        select.addEventListener("change", apply);
        apply();
    });
})();
