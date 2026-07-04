// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/dashboard
// Interações leves: ainda sem chart libraries (entram na próxima
// iteração com Chart.js servido localmente — CSP é 'self').
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        // Persistência do toggle "ocultar valores" entre telas. O atributo
        // data-hide-values é aplicado pelo theme.js no <body>; aqui só
        // garantimos que a UI continua reagindo.
        var hideBtn = document.querySelector("[data-toggle-hide-values]");
        if (!hideBtn) return;

        hideBtn.addEventListener("click", function () {
            var body = document.body;
            var on = body.getAttribute("data-hide-values") === "1";
            body.setAttribute("data-hide-values", on ? "0" : "1");
            try {
                localStorage.setItem("rastroos.hideValues", on ? "0" : "1");
            } catch (e) {
                // localStorage indisponível — silencia, é apenas UX
            }
        });
    });
})();
