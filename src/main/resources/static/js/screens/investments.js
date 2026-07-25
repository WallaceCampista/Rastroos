// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/investments
// Cor dos cofrinhos (--c) + anel de progresso (stroke/offset) via
// CSSOM (compatível com a CSP). Confirmação de forms com data-confirm.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll("[data-piggy-color]").forEach(function (el) {
            var c = el.getAttribute("data-piggy-color");
            if (c) el.style.setProperty("--c", c);
        });

        var CIRC = 2 * Math.PI * 55; // r = 55 (anel 120px, igual ao protótipo)
        var reduce = window.matchMedia
            && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        document.querySelectorAll(".pr-fill").forEach(function (ring) {
            var pct = Math.max(0, Math.min(100, parseFloat(ring.getAttribute("data-percent")) || 0));
            var color = ring.getAttribute("data-color");
            if (color) ring.style.stroke = color;
            ring.style.transition = "none";
            var card = ring.closest(".piggy-card");
            var pctEl = card ? card.querySelector(".piggy-ring-pct") : null;

            function paint(cur) {
                ring.style.strokeDashoffset = String(CIRC * (1 - cur / 100));
                if (pctEl) pctEl.textContent = Math.round(cur) + "%";
            }
            if (reduce) { paint(pct); return; }

            // anel cresce + % conta 0 → alvo (easeOutCubic ~1100ms)
            var start = null;
            function tick(now) {
                if (start === null) start = now;
                var t = Math.min(1, (now - start) / 1100);
                paint(pct * (1 - Math.pow(1 - t, 3)));
                if (t < 1) requestAnimationFrame(tick);
                else paint(pct);
            }
            paint(0);
            requestAnimationFrame(tick);
        });

        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (e) {
                if (!window.confirm(form.getAttribute("data-confirm") || "Confirmar?")) {
                    e.preventDefault();
                }
            });
        });
    });
})();
