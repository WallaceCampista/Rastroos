// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/manager (Alfredo)
//   - Rola o thread para a última mensagem ao abrir.
//   - Confirm para forms com data-confirm (apagar conversa).
//   - Enter envia; Shift+Enter quebra linha.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var thread = document.getElementById("mgrThread");
        if (thread) {
            thread.scrollTop = thread.scrollHeight;
        }

        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                var message = form.getAttribute("data-confirm") || "Confirmar?";
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });

        document.querySelectorAll(".mgr-composer .mgr-input").forEach(function (input) {
            input.addEventListener("keydown", function (event) {
                if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    if (input.value.trim().length > 0) {
                        input.form.requestSubmit();
                    }
                }
            });
        });
    });
})();
