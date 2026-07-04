// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/expenses/{new,edit}
// Mostra um aviso quando o usuário pede mais de 1 parcela.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var input = document.getElementById("installments");
        if (!input) return;
        var help = input.parentNode.querySelector(".field-help");
        if (!help) return;
        var defaultText = help.textContent;

        function update() {
            var n = parseInt(input.value, 10);
            if (isFinite(n) && n > 1) {
                help.textContent = "Serão criados " + n + " lançamentos, um por mês, " +
                                   "a partir da data informada.";
            } else {
                help.textContent = defaultText;
            }
        }
        input.addEventListener("input", update);
        update();
    });
})();
