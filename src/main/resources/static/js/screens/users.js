// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/users (admin)
//   Confirmação para ações destrutivas (forms com data-confirm):
//   desativar, resetar senha, excluir, encerrar sessões.
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
    });
})();
