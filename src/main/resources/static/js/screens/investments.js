// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/investments
//   - Confirm para forms com data-confirm.
//   - Toggle do mini-form de histórico (data-history-toggle).
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

        document.querySelectorAll("[data-history-toggle]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var target = document.querySelector(btn.getAttribute("data-target"));
                if (!target) return;
                target.hidden = !target.hidden;
            });
        });
    });
})();
