// ─────────────────────────────────────────────────────────────
// Rastro$ — /app/income
//   - Gráfico "Últimos 6 meses" (área verde) em canvas vanilla.
//   - Confirm para forms com data-confirm.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    function drawIncomeChart() {
        var canvas = document.getElementById("incomeChart");
        var el = document.getElementById("incomeChartData");
        if (!canvas || !el) return;
        var points;
        var text = el.textContent || "";
        try {
            points = (JSON.parse(text) || {}).points || [];
        } catch (e) {
            // <script> não decodifica entidades; th:text escapa as aspas.
            try {
                points = (JSON.parse(text
                    .replace(/&quot;/g, "\"").replace(/&#39;/g, "'")
                    .replace(/&lt;/g, "<").replace(/&gt;/g, ">")
                    .replace(/&amp;/g, "&")) || {}).points || [];
            } catch (e2) { return; }
        }
        if (!points.length) return;

        var ratio = window.devicePixelRatio || 1;
        var rect = canvas.getBoundingClientRect();
        if (rect.width < 2) return;
        canvas.width = Math.floor(rect.width * ratio);
        canvas.height = Math.floor(rect.height * ratio);
        var ctx = canvas.getContext("2d");
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);

        var w = rect.width, h = rect.height;
        var padL = 10, padR = 10, padT = 14, padB = 24;
        var iw = w - padL - padR, ih = h - padT - padB;
        var ys = points.map(function (p) { return p.y; });
        // base fixa em 0, como o LineChart do protótipo (min(0,…), max(1,…))
        var minY = Math.min.apply(null, [0].concat(ys));
        var maxY = Math.max.apply(null, [1].concat(ys));
        var range = (maxY - minY) || 1;
        var X = function (i) { return padL + (points.length <= 1 ? iw / 2 : (i / (points.length - 1)) * iw); };
        var Y = function (v) { return padT + ih - ((v - minY) / range) * ih; };
        var color = "#34d399";

        function tracePath() {
            ctx.beginPath();
            ctx.moveTo(X(0), Y(points[0].y));
            for (var k = 1; k < points.length; k++) {
                var px = X(k - 1), py = Y(points[k - 1].y);
                var x = X(k), y = Y(points[k].y);
                ctx.bezierCurveTo(px + (x - px) * 0.5, py, x - (x - px) * 0.5, y, x, y);
            }
        }

        // grade horizontal tracejada
        ctx.strokeStyle = (getComputedStyle(document.body).getPropertyValue("--border") || "rgba(255,255,255,0.09)").trim();
        ctx.lineWidth = 1;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        for (var g = 0; g <= 4; g++) { var gy = padT + (g / 4) * ih; ctx.moveTo(padL, gy); ctx.lineTo(padL + iw, gy); }
        ctx.stroke();
        ctx.setLineDash([]);

        // área chapada
        tracePath();
        ctx.lineTo(X(points.length - 1), padT + ih);
        ctx.lineTo(X(0), padT + ih);
        ctx.closePath();
        ctx.globalAlpha = 0.15;
        ctx.fillStyle = color;
        ctx.fill();
        ctx.globalAlpha = 1;

        // curva
        tracePath();
        ctx.strokeStyle = color;
        ctx.lineWidth = 2.2;
        ctx.lineJoin = "round";
        ctx.lineCap = "round";
        ctx.stroke();

        // ponto no último mês
        ctx.beginPath();
        ctx.arc(X(points.length - 1), Y(points[points.length - 1].y), 3, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();

        var dim = (getComputedStyle(document.body).getPropertyValue("--text-dim") || "#98a2c7").trim();
        ctx.fillStyle = dim;
        ctx.font = "10px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        for (var m = 0; m < points.length; m++) {
            ctx.fillText(String(points[m].x), X(m), padT + ih + 8);
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        drawIncomeChart();
        var pending;
        window.addEventListener("resize", function () {
            if (pending) cancelAnimationFrame(pending);
            pending = requestAnimationFrame(drawIncomeChart);
        });
        window.addEventListener("rastroos:themechange", drawIncomeChart);

        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                if (!window.confirm(form.getAttribute("data-confirm") || "Confirmar?")) {
                    event.preventDefault();
                }
            });
        });
    });
})();
