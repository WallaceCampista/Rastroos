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
    });
})();
