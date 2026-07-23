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
        var text = el.textContent || el.innerText || "";
        try {
            return JSON.parse(text);
        } catch (e) {
            // <script> não decodifica entidades HTML; th:text escapa aspas
            // (&quot;). Decodificamos as 5 entidades padrão antes de parsear.
            try {
                return JSON.parse(text
                    .replace(/&quot;/g, "\"").replace(/&#39;/g, "'")
                    .replace(/&lt;/g, "<").replace(/&gt;/g, ">")
                    .replace(/&amp;/g, "&"));
            } catch (e2) {
                return null;
            }
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

    // ── Line chart (gasto do mês, "onda" suave) ──────────────
    // Espelha o LineChart do protótipo: curva cubic-bézier, área
    // chapada (0.15), grade tracejada, ponto só no último dia,
    // rótulos apenas no eixo X (sem eixo Y), base fixa em 0.
    function drawLineChart(canvas, points) {
        if (!canvas || !points || points.length === 0) return;
        var fit = fitCanvas(canvas);
        var ctx = fit.ctx, w = fit.w, h = fit.h;

        var padL = 8, padR = 8, padT = 10, padB = 22;
        var innerW = w - padL - padR;
        var innerH = h - padT - padB;

        var minY = 0, maxY = 1;
        for (var i = 0; i < points.length; i++) {
            if (points[i].y < minY) minY = points[i].y;
            if (points[i].y > maxY) maxY = points[i].y;
        }
        var range = (maxY - minY) || 1;

        function X(i) {
            return padL + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW);
        }
        function Y(v) {
            return padT + innerH - ((v - minY) / range) * innerH;
        }

        ctx.clearRect(0, 0, w, h);

        // grade horizontal tracejada (4 divisões, sem rótulo Y)
        ctx.strokeStyle = cssVar("--border", "rgba(255,255,255,0.09)");
        ctx.lineWidth = 1;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        for (var g = 0; g <= 4; g++) {
            var gy = padT + (g / 4) * innerH;
            ctx.moveTo(padL, gy);
            ctx.lineTo(padL + innerW, gy);
        }
        ctx.stroke();
        ctx.setLineDash([]);

        var primary = cssVar("--primary", "#6366f1");

        function tracePath() {
            ctx.beginPath();
            ctx.moveTo(X(0), Y(points[0].y));
            for (var k = 1; k < points.length; k++) {
                var px = X(k - 1), py = Y(points[k - 1].y);
                var x = X(k), y = Y(points[k].y);
                var cx1 = px + (x - px) * 0.5;
                var cx2 = x - (x - px) * 0.5;
                ctx.bezierCurveTo(cx1, py, cx2, y, x, y);
            }
        }

        // área chapada sob a curva
        tracePath();
        ctx.lineTo(X(points.length - 1), padT + innerH);
        ctx.lineTo(X(0), padT + innerH);
        ctx.closePath();
        ctx.globalAlpha = 0.15;
        ctx.fillStyle = primary;
        ctx.fill();
        ctx.globalAlpha = 1;

        // traço da curva
        tracePath();
        ctx.strokeStyle = primary;
        ctx.lineWidth = 2.2;
        ctx.lineJoin = "round";
        ctx.lineCap = "round";
        ctx.stroke();

        // ponto no último dia
        ctx.beginPath();
        ctx.arc(X(points.length - 1), Y(points[points.length - 1].y), 3, 0, Math.PI * 2);
        ctx.fillStyle = primary;
        ctx.fill();

        // rótulos do eixo X (esparsos: 1, 6, 11, …, último)
        ctx.fillStyle = cssVar("--text-dim", "rgba(244,244,248,0.62)");
        ctx.font = "10px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        for (var j = 0; j < points.length; j++) {
            var lbl = points[j].x;
            if (j === 0 || j === points.length - 1 || lbl % 5 === 0) {
                ctx.fillText(String(lbl), X(j), padT + innerH + 6);
            }
        }
    }

    // ── Donut chart (gastos por categoria) ───────────────────
    // Espelha o Donut do protótipo: anéis com traço + folga entre
    // fatias + trilho, com animação de revelação (progress 0→1).
    function drawDonutChart(canvas, segments, progress) {
        if (!canvas) return;
        if (progress == null) progress = 1;
        var fit = fitCanvas(canvas);
        var ctx = fit.ctx, w = fit.w, h = fit.h;

        var cx = w / 2, cy = h / 2;
        var thickness = Math.max(14, Math.min(w, h) * 0.16);
        var radius = Math.min(w, h) / 2 - thickness / 2 - 2;

        ctx.clearRect(0, 0, w, h);

        // trilho de fundo
        ctx.strokeStyle = cssVar("--surface-3", "#232c47");
        ctx.lineWidth = thickness;
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.stroke();

        var total = 0;
        for (var s = 0; s < segments.length; s++) total += segments[s].value;

        if (total > 0) {
            var gap = 0.015 * Math.PI * 2;   // folga entre fatias (rad)
            var acc = -Math.PI / 2;          // começa no topo
            for (var i = 0; i < segments.length; i++) {
                var frac = (segments[i].value / total) * progress;
                var ang = frac * Math.PI * 2;
                if (ang > gap) {
                    ctx.strokeStyle = segments[i].color || cssVar("--primary", "#6366f1");
                    ctx.lineWidth = thickness;
                    ctx.beginPath();
                    ctx.arc(cx, cy, radius, acc + gap / 2, acc + ang - gap / 2);
                    ctx.stroke();
                }
                acc += ang;
            }
        }

        // centro: valor (grande) + rótulo (pequeno)
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillStyle = cssVar("--text", "#f4f4f8");
        ctx.font = "800 17px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.fillText("R$ " + shortMoney(total), cx, cy - 6);
        ctx.fillStyle = cssVar("--text-dim", "#98a2c7");
        ctx.font = "600 10px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.fillText("total gasto", cx, cy + 13);
    }

    // Anima o donut (revelação 0→1, easeOutCubic ~900ms).
    function animateDonut(canvas, segments) {
        var reduce = window.matchMedia
            && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        if (reduce) { drawDonutChart(canvas, segments, 1); return; }
        var start = null;
        function tick(now) {
            if (start === null) start = now;
            var t = Math.min(1, (now - start) / 900);
            var eased = 1 - Math.pow(1 - t, 3);
            drawDonutChart(canvas, segments, eased);
            if (t < 1) requestAnimationFrame(tick);
        }
        requestAnimationFrame(tick);
    }

    function init() {
        var data = readJson("chartData");
        if (!data) return;

        // "Gastos no mês · Dia a dia": série de gasto lançado por dia (1..N).
        var spendCanvas = document.getElementById("spendChart");
        var spendPoints = Array.isArray(data.dailySpend)
            ? data.dailySpend.map(function (v, i) { return { x: i + 1, y: v }; })
            : [];
        if (spendCanvas && spendPoints.length) {
            drawLineChart(spendCanvas, spendPoints);
        }

        var donutCanvas = document.getElementById("categoryDonut");
        if (donutCanvas && Array.isArray(data.byCategory)) {
            animateDonut(donutCanvas, data.byCategory);
        }

        function redraw() {
            if (spendCanvas && spendPoints.length) drawLineChart(spendCanvas, spendPoints);
            if (donutCanvas && Array.isArray(data.byCategory)) drawDonutChart(donutCanvas, data.byCategory, 1);
        }

        var pending;
        window.addEventListener("resize", function () {
            if (pending) cancelAnimationFrame(pending);
            pending = requestAnimationFrame(redraw);
        });
        // redesenha ao trocar a paleta/tema (canvas não reage a var(--primary))
        window.addEventListener("rastroos:themechange", redraw);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
