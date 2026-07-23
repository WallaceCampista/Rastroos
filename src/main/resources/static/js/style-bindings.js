/* ─────────────────────────────────────────────────────────────
   Rastro$ — style bindings (CSP-safe)
   ─────────────────────────────────────────────────────────────
   Aplica estilos dinâmicos (cores/larguras vindas de dados do
   usuário) SEM atributo `style="..."` inline — que a CSP
   `style-src 'self'` (sem unsafe-inline) bloquearia. Mutações via
   CSSOM (element.style.*) NÃO são governadas por style-src, então
   este é o caminho compatível com a CSP estrita.

   Contrato de marcação (data-*):
     data-fill="<cor>"          → background
     data-stroke="<cor>"        → border-color
     data-bar-width="<n>"       → width: n%
     data-bar-height="<n>"      → height: n%
     data-bar-bottom="<n>"      → bottom: n%
     data-bar-top="<n>"         → top: n%
   Valores são atribuídos via CSSOM: strings inválidas são
   simplesmente ignoradas pelo browser (não há execução de código).
   ───────────────────────────────────────────────────────────── */
(function () {
    "use strict";

    function applyEach(selector, fn) {
        document.querySelectorAll(selector).forEach(fn);
    }

    document.addEventListener("DOMContentLoaded", function () {
        applyEach("[data-fill]", function (el) {
            el.style.background = el.getAttribute("data-fill");
        });
        applyEach("[data-stroke]", function (el) {
            el.style.borderColor = el.getAttribute("data-stroke");
        });
        applyEach("[data-bar-width]", function (el) {
            el.style.width = el.getAttribute("data-bar-width") + "%";
        });
        applyEach("[data-bar-height]", function (el) {
            el.style.height = el.getAttribute("data-bar-height") + "%";
        });
        applyEach("[data-bar-bottom]", function (el) {
            el.style.bottom = el.getAttribute("data-bar-bottom") + "%";
        });
        applyEach("[data-bar-top]", function (el) {
            el.style.top = el.getAttribute("data-bar-top") + "%";
        });
    });
})();
