// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/investments/{new,edit}
// Mostra/oculta campos específicos por tipo (lê data-only-kind).
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var select = document.querySelector("[data-inv-kind]");
        if (!select) return;
        var conditional = document.querySelectorAll("[data-only-kind]");

        function apply() {
            var current = select.value;
            conditional.forEach(function (field) {
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
