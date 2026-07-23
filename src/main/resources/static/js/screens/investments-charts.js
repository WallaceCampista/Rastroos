/* ─────────────────────────────────────────────────────────────
   Rastro$ — /app/investments
   Onda de evolução do patrimônio (hero) + sparklines da carteira.
   Curvas cubic-bézier iguais ao LineChart do protótipo. Canvas
   vanilla (CSP-safe); redesenha em resize e ao trocar a paleta.
   ───────────────────────────────────────────────────────────── */
(function () {
    "use strict";

    function cssVar(name, fallback) {
        var v = getComputedStyle(document.body).getPropertyValue(name);
        return (v || "").trim() || fallback;
    }

    function readJson(id) {
        var el = document.getElementById(id);
        if (!el) return null;
        var text = el.textContent || "";
        try { return JSON.parse(text); }
        catch (e) {
            try {
                return JSON.parse(text
                    .replace(/&quot;/g, "\"").replace(/&#39;/g, "'")
                    .replace(/&lt;/g, "<").replace(/&gt;/g, ">")
                    .replace(/&amp;/g, "&"));
            } catch (e2) { return null; }
        }
    }

    function fit(canvas) {
        var ratio = window.devicePixelRatio || 1;
        var rect = canvas.getBoundingClientRect();
        if (rect.width < 2) return null;
        canvas.width = Math.floor(rect.width * ratio);
        canvas.height = Math.floor(rect.height * ratio);
        var ctx = canvas.getContext("2d");
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        return { ctx: ctx, w: rect.width, h: rect.height };
    }

    // Desenha uma onda suave (área + linha). opts: {labels, color, grid, dot, pad}
    function wave(canvas, values, opts) {
        if (!canvas || !values || values.length === 0) return;
        var f = fit(canvas);
        if (!f) return;
        var ctx = f.ctx, w = f.w, h = f.h;
        opts = opts || {};
        var pad = opts.pad || { l: 6, r: 6, t: 8, b: opts.labels ? 22 : 6 };
        var iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;

        var minY = 0, maxY = 1;
        for (var i = 0; i < values.length; i++) {
            if (values[i] < minY) minY = values[i];
            if (values[i] > maxY) maxY = values[i];
        }
        var range = (maxY - minY) || 1;
        var X = function (i) { return pad.l + (values.length === 1 ? iw / 2 : (i / (values.length - 1)) * iw); };
        var Y = function (v) { return pad.t + ih - ((v - minY) / range) * ih; };

        ctx.clearRect(0, 0, w, h);

        if (opts.grid) {
            ctx.strokeStyle = cssVar("--border", "rgba(255,255,255,0.09)");
            ctx.lineWidth = 1;
            ctx.setLineDash([3, 3]);
            ctx.beginPath();
            for (var g = 0; g <= 4; g++) { var gy = pad.t + (g / 4) * ih; ctx.moveTo(pad.l, gy); ctx.lineTo(pad.l + iw, gy); }
            ctx.stroke();
            ctx.setLineDash([]);
        }

        var color = opts.color || cssVar("--primary", "#6366f1");
        function trace() {
            ctx.beginPath();
            ctx.moveTo(X(0), Y(values[0]));
            for (var k = 1; k < values.length; k++) {
                var px = X(k - 1), py = Y(values[k - 1]);
                var x = X(k), y = Y(values[k]);
                ctx.bezierCurveTo(px + (x - px) * 0.5, py, x - (x - px) * 0.5, y, x, y);
            }
        }
        // área
        trace();
        ctx.lineTo(X(values.length - 1), pad.t + ih);
        ctx.lineTo(X(0), pad.t + ih);
        ctx.closePath();
        ctx.globalAlpha = 0.15; ctx.fillStyle = color; ctx.fill(); ctx.globalAlpha = 1;
        // linha
        trace();
        ctx.strokeStyle = color; ctx.lineWidth = opts.thin ? 1.6 : 2.2;
        ctx.lineJoin = "round"; ctx.lineCap = "round"; ctx.stroke();
        // ponto no fim
        if (opts.dot) {
            ctx.beginPath();
            ctx.arc(X(values.length - 1), Y(values[values.length - 1]), 3, 0, Math.PI * 2);
            ctx.fillStyle = color; ctx.fill();
        }
        // rótulos X
        if (opts.labels) {
            ctx.fillStyle = cssVar("--text-dim", "rgba(244,244,248,0.62)");
            ctx.font = "10px 'Plus Jakarta Sans', system-ui, sans-serif";
            ctx.textAlign = "center"; ctx.textBaseline = "top";
            for (var j = 0; j < opts.labels.length; j++) {
                ctx.fillText(String(opts.labels[j]), X(j), pad.t + ih + 6);
            }
        }
    }

    function drawAll(data) {
        var hero = document.getElementById("investHeroChart");
        if (hero && Array.isArray(data.total) && data.total.length) {
            wave(hero, data.total, { labels: data.labels, color: cssVar("--accent", "#fbbf24"), grid: true, dot: true });
        }
        document.querySelectorAll("canvas[data-spark-id]").forEach(function (cv) {
            var id = cv.getAttribute("data-spark-id");
            var series = data.sparklines && data.sparklines[id];
            if (Array.isArray(series) && series.length > 1) {
                wave(cv, series, { color: cv.getAttribute("data-spark-color") || cssVar("--primary", "#6366f1"), thin: true });
            }
        });
    }

    function init() {
        var data = readJson("investChartData");
        if (!data) return;
        drawAll(data);
        var pending;
        window.addEventListener("resize", function () {
            if (pending) cancelAnimationFrame(pending);
            pending = requestAnimationFrame(function () { drawAll(data); });
        });
        window.addEventListener("rastroos:themechange", function () { drawAll(data); });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
