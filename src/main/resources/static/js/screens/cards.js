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
    });
})();
