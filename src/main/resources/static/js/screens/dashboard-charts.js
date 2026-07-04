// ─────────────────────────────────────────────────────────────
// Rastro$ — gráficos do dashboard em canvas vanilla
//
// Sem dependências externas: respeita CSP `script-src 'self'` sem
// precisar servir uma lib de gráficos. Apenas dois desenhos —
// LineChart (saldo do mês) e DonutChart (gastos por categoria) —
// proporcionais ao tamanho do canvas e responsivos.
//
// Os dados ficam em JSON inline (gerado pelo Thymeleaf) dentro de
// <script type="application/json" id="chartData">.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    function readJson(id) {
        var el = document.getElementById(id);
        if (!el) return null;
        try {
            return JSON.parse(el.textContent || el.innerText || "null");
        } catch (e) {
            return null;
        }
    }

    function cssVar(name, fallback) {
        var v = getComputedStyle(document.body).getPropertyValue(name);
        v = (v || "").trim();
        return v || fallback;
    }

    function fitCanvas(canvas) {
        var ratio = window.devicePixelRatio || 1;
        var rect = canvas.getBoundingClientRect();
        canvas.width = Math.max(1, Math.floor(rect.width * ratio));
        canvas.height = Math.max(1, Math.floor(rect.height * ratio));
        var ctx = canvas.getContext("2d");
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        return { ctx: ctx, w: rect.width, h: rect.height };
    }

    function shortMoney(value) {
        var abs = Math.abs(value);
        if (abs >= 1000) return (value / 1000).toFixed(1).replace(/\.0$/, "") + "k";
        return String(Math.round(value));
    }

    // ── Line chart (saldo do mês) ────────────────────────────
    function drawLineChart(canvas, points) {
        if (!canvas || !points || points.length === 0) return;
        var fit = fitCanvas(canvas);
        var ctx = fit.ctx;
        var w = fit.w;
        var h = fit.h;

        var padL = 36, padR = 12, padT = 14, padB = 22;
        var innerW = w - padL - padR;
        var innerH = h - padT - padB;

        var minY = points[0].y;
        var maxY = points[0].y;
        for (var i = 1; i < points.length; i++) {
            if (points[i].y < minY) minY = points[i].y;
            if (points[i].y > maxY) maxY = points[i].y;
        }
        if (minY === maxY) { minY -= 1; maxY += 1; }
        var range = maxY - minY;

        function x(i) {
            return padL + (i / (points.length - 1)) * innerW;
        }
        function y(v) {
            return padT + innerH - ((v - minY) / range) * innerH;
        }

        ctx.clearRect(0, 0, w, h);

        // grid horizontal
        ctx.strokeStyle = cssVar("--border", "rgba(255,255,255,0.09)");
        ctx.lineWidth = 1;
        ctx.beginPath();
        for (var g = 0; g <= 4; g++) {
            var gy = padT + (g / 4) * innerH;
            ctx.moveTo(padL, gy);
            ctx.lineTo(padL + innerW, gy);
        }
        ctx.stroke();

        // y axis labels
        ctx.fillStyle = cssVar("--text-dim", "rgba(244,244,248,0.62)");
        ctx.font = "10px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.textAlign = "right";
        ctx.textBaseline = "middle";
        for (var k = 0; k <= 4; k++) {
            var v = maxY - (k / 4) * range;
            ctx.fillText(shortMoney(v), padL - 6, padT + (k / 4) * innerH);
        }

        // line area fill
        var grad = ctx.createLinearGradient(0, padT, 0, padT + innerH);
        var primary = cssVar("--primary", "#6366f1");
        grad.addColorStop(0, primary + "55");
        grad.addColorStop(1, primary + "00");
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.moveTo(x(0), y(points[0].y));
        for (var n = 1; n < points.length; n++) ctx.lineTo(x(n), y(points[n].y));
        ctx.lineTo(x(points.length - 1), padT + innerH);
        ctx.lineTo(x(0), padT + innerH);
        ctx.closePath();
        ctx.fill();

        // line stroke
        ctx.strokeStyle = primary;
        ctx.lineWidth = 2;
        ctx.lineJoin = "round";
        ctx.beginPath();
        ctx.moveTo(x(0), y(points[0].y));
        for (var p = 1; p < points.length; p++) ctx.lineTo(x(p), y(points[p].y));
        ctx.stroke();

        // x axis sparse labels (every ~5 days)
        ctx.fillStyle = cssVar("--text-dim", "rgba(244,244,248,0.62)");
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        for (var j = 0; j < points.length; j++) {
            var lbl = points[j].x;
            if (j === 0 || j === points.length - 1 || lbl % 5 === 0) {
                ctx.fillText(String(lbl), x(j), padT + innerH + 4);
            }
        }
    }

    // ── Donut chart (gastos por categoria) ───────────────────
    function drawDonutChart(canvas, segments) {
        if (!canvas) return;
        var fit = fitCanvas(canvas);
        var ctx = fit.ctx;
        var w = fit.w;
        var h = fit.h;

        var cx = w / 2;
        var cy = h / 2;
        var outerR = Math.min(w, h) / 2 - 6;
        var innerR = outerR - 22;

        ctx.clearRect(0, 0, w, h);

        var total = 0;
        for (var s = 0; s < segments.length; s++) total += segments[s].value;
        if (total <= 0) {
            // donut vazio
            ctx.strokeStyle = cssVar("--border", "rgba(255,255,255,0.09)");
            ctx.lineWidth = outerR - innerR;
            ctx.beginPath();
            ctx.arc(cx, cy, (outerR + innerR) / 2, 0, Math.PI * 2);
            ctx.stroke();
            return;
        }

        var start = -Math.PI / 2;
        for (var i = 0; i < segments.length; i++) {
            var slice = (segments[i].value / total) * Math.PI * 2;
            ctx.fillStyle = segments[i].color || cssVar("--primary", "#6366f1");
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, outerR, start, start + slice);
            ctx.closePath();
            ctx.fill();
            start += slice;
        }

        // hollow center
        ctx.globalCompositeOperation = "destination-out";
        ctx.beginPath();
        ctx.arc(cx, cy, innerR, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalCompositeOperation = "source-over";

        // center label
        ctx.fillStyle = cssVar("--text", "#f4f4f8");
        ctx.font = "700 13px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText("Total", cx, cy - 8);
        ctx.font = "800 16px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.fillText("R$ " + shortMoney(total), cx, cy + 10);
    }

    function init() {
        var data = readJson("chartData");
        if (!data) return;

        var balanceCanvas = document.getElementById("balanceChart");
        if (balanceCanvas && Array.isArray(data.balance)) {
            drawLineChart(balanceCanvas, data.balance);
        }

        var donutCanvas = document.getElementById("categoryDonut");
        if (donutCanvas && Array.isArray(data.byCategory)) {
            drawDonutChart(donutCanvas, data.byCategory);
        }

        var pending;
        window.addEventListener("resize", function () {
            if (pending) cancelAnimationFrame(pending);
            pending = requestAnimationFrame(function () {
                if (balanceCanvas && Array.isArray(data.balance)) {
                    drawLineChart(balanceCanvas, data.balance);
                }
                if (donutCanvas && Array.isArray(data.byCategory)) {
                    drawDonutChart(donutCanvas, data.byCategory);
                }
            });
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
