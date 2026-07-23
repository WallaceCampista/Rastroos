/* ─────────────────────────────────────────────────────────────
   Rastro$ — micro-interações de valores (AnimatedMoney + useTilt
   do protótipo React), global para todas as telas:
     • count-up: o valor conta de 0 até o total no load
     • tilt: leve inclinação 3D dos cards clicáveis seguindo o cursor
   Tudo via CSSOM (element.style.*), compatível com a CSP estrita.
   Respeita prefers-reduced-motion.
   ───────────────────────────────────────────────────────────── */
(function () {
    "use strict";

    // Valores monetários que animam ao carregar.
    var COUNT_SELECTOR = ".kpi-value[data-amount], .income-hero-value[data-amount], "
        + ".invest-hero-value[data-amount]";
    // Cards que inclinam 3D ao passar o cursor.
    var TILT_SELECTOR = ".kpi-card.is-clickable, .invest-hero, .piggy-card";

    var reduce = window.matchMedia
        && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    function fmt(n) {
        return "R$ " + n.toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function countUp(el) {
        var raw = el.getAttribute("data-amount");
        if (raw === null || raw === "") return;   // mascarado / sem valor
        var target = parseFloat(raw);
        if (!isFinite(target)) return;
        if (reduce) { el.textContent = fmt(target); return; }

        var dur = 720, start = null;
        function step(ts) {
            if (start === null) start = ts;
            var p = Math.min(1, (ts - start) / dur);
            var eased = 1 - Math.pow(1 - p, 3);    // easeOutCubic
            el.textContent = fmt(target * eased);
            if (p < 1) requestAnimationFrame(step);
            else el.textContent = fmt(target);
        }
        requestAnimationFrame(step);
    }

    function wireTilt(card) {
        if (reduce) return;
        var MAX = 6, LIFT = 4;
        card.addEventListener("mouseenter", function () {
            card.style.transition = "transform 0s";
        });
        card.addEventListener("mousemove", function (e) {
            var r = card.getBoundingClientRect();
            var px = (e.clientX - r.left) / r.width - 0.5;
            var py = (e.clientY - r.top) / r.height - 0.5;
            card.style.transform = "perspective(620px) rotateX(" + (-py * MAX)
                + "deg) rotateY(" + (px * MAX) + "deg) translateY(-" + LIFT + "px)";
        });
        card.addEventListener("mouseleave", function () {
            card.style.transition = "";           // volta à transição do CSS
            card.style.transform = "";
        });
    }

    function init() {
        document.querySelectorAll(COUNT_SELECTOR).forEach(countUp);
        document.querySelectorAll(TILT_SELECTOR).forEach(wireTilt);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
