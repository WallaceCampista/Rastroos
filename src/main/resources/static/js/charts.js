// ─────────────────────────────────────────────────────────────
// Rastro$ — motor de gráficos em canvas vanilla, reutilizável
//
// Sem dependências externas: respeita a CSP `script-src 'self'` sem
// servir libs de terceiros. Expõe window.RastroCharts com:
//   • donut(canvas, segments, opts)   — rosca proporcional
//   • multiLine(canvas, {labels, series}) — várias linhas com eixo
//   • onResize(fn)                    — redesenho responsivo (rAF)
//
// Os dados vêm de JSON inline (gerado pelo Thymeleaf) — nunca th:utext.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

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

    // ── Donut ────────────────────────────────────────────────
    function donut(canvas, segments, opts) {
        if (!canvas) return;
        opts = opts || {};
        var fit = fitCanvas(canvas);
        var ctx = fit.ctx, w = fit.w, h = fit.h;
        var cx = w / 2, cy = h / 2;
        var outerR = Math.min(w, h) / 2 - 6;
        var innerR = outerR - 24;

        ctx.clearRect(0, 0, w, h);

        var total = 0;
        for (var s = 0; s < segments.length; s++) total += segments[s].value;

        if (total <= 0) {
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

        // furo central
        ctx.globalCompositeOperation = "destination-out";
        ctx.beginPath();
        ctx.arc(cx, cy, innerR, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalCompositeOperation = "source-over";

        ctx.fillStyle = cssVar("--text-dim", "rgba(244,244,248,0.62)");
        ctx.font = "600 11px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(opts.centerLabel || "Total", cx, cy - 9);
        ctx.fillStyle = cssVar("--text", "#f4f4f8");
        ctx.font = "800 16px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.fillText("R$ " + shortMoney(total), cx, cy + 9);
    }

    // ── Multi-line com eixo ──────────────────────────────────
    function multiLine(canvas, data) {
        if (!canvas || !data || !Array.isArray(data.series) || data.series.length === 0) return;
        var labels = data.labels || [];
        var series = data.series;

        var fit = fitCanvas(canvas);
        var ctx = fit.ctx, w = fit.w, h = fit.h;
        var padL = 40, padR = 14, padT = 12, padB = 24;
        var innerW = w - padL - padR;
        var innerH = h - padT - padB;

        var n = 0;
        for (var a = 0; a < series.length; a++) n = Math.max(n, series[a].values.length);
        if (n === 0) return;

        var minY = 0, maxY = 0;
        for (var b = 0; b < series.length; b++) {
            for (var c = 0; c < series[b].values.length; c++) {
                var v = series[b].values[c];
                if (v < minY) minY = v;
                if (v > maxY) maxY = v;
            }
        }
        if (minY === maxY) { maxY += 1; }
        var range = maxY - minY;

        function x(i) { return padL + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW); }
        function y(val) { return padT + innerH - ((val - minY) / range) * innerH; }

        ctx.clearRect(0, 0, w, h);

        // grid + rótulos do eixo Y
        ctx.strokeStyle = cssVar("--border", "rgba(255,255,255,0.09)");
        ctx.fillStyle = cssVar("--text-dim", "rgba(244,244,248,0.62)");
        ctx.lineWidth = 1;
        ctx.font = "10px 'Plus Jakarta Sans', system-ui, sans-serif";
        ctx.textAlign = "right";
        ctx.textBaseline = "middle";
        ctx.setLineDash([2, 3]);
        for (var g = 0; g <= 4; g++) {
            var gy = padT + (g / 4) * innerH;
            ctx.beginPath();
            ctx.moveTo(padL, gy);
            ctx.lineTo(padL + innerW, gy);
            ctx.stroke();
            ctx.fillText(shortMoney(maxY - (g / 4) * range), padL - 6, gy);
        }
        ctx.setLineDash([]);

        // rótulos do eixo X
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        for (var l = 0; l < labels.length; l++) {
            ctx.fillText(labels[l], x(l), padT + innerH + 6);
        }

        // linhas
        for (var s = 0; s < series.length; s++) {
            var color = series[s].color || cssVar("--primary", "#6366f1");
            var vals = series[s].values;
            if (vals.length === 0) continue;

            // curva suave (bézier com control points no meio horizontal)
            function wavePath() {
                ctx.moveTo(x(0), y(vals[0]));
                for (var p = 1; p < vals.length; p++) {
                    var px = x(p - 1), py = y(vals[p - 1]);
                    var cx = x(p), cy = y(vals[p]);
                    var mid = px + (cx - px) * 0.5;
                    ctx.bezierCurveTo(mid, py, mid, cy, cx, cy);
                }
            }

            if (series[s].area) {
                var grad = ctx.createLinearGradient(0, padT, 0, padT + innerH);
                grad.addColorStop(0, color + "33");
                grad.addColorStop(1, color + "00");
                ctx.fillStyle = grad;
                ctx.beginPath();
                wavePath();
                ctx.lineTo(x(vals.length - 1), y(minY));
                ctx.lineTo(x(0), y(minY));
                ctx.closePath();
                ctx.fill();
            }

            ctx.strokeStyle = color;
            ctx.lineWidth = 2;
            ctx.lineJoin = "round";
            ctx.lineCap = "round";
            ctx.beginPath();
            wavePath();
            ctx.stroke();

            // dot só no último ponto (como no protótipo)
            var last = vals.length - 1;
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(x(last), y(vals[last]), 3, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    // ── Treemap (slice-and-dice, igual ao protótipo) ─────────
    function treemap(container, items, opts) {
        if (!container) return;
        opts = opts || {};
        var H = opts.height || 280;
        var W = Math.max(1, container.clientWidth || 600);
        var data = (items || [])
            .filter(function (d) { return d && d.value > 0; })
            .slice()
            .sort(function (a, b) { return b.value - a.value; });
        container.innerHTML = "";
        if (data.length === 0) return;

        function layout(list, x, y, w, h, horiz) {
            if (list.length === 1) return [{ d: list[0], x: x, y: y, w: w, h: h }];
            var sum = 0, i;
            for (i = 0; i < list.length; i++) sum += list[i].value;
            var head = list[0], rest = list.slice(1);
            var frac = head.value / sum;
            if (horiz) {
                var hw = w * frac;
                return [{ d: head, x: x, y: y, w: hw, h: h }]
                    .concat(layout(rest, x + hw, y, w - hw, h, !horiz));
            }
            var hh = h * frac;
            return [{ d: head, x: x, y: y, w: w, h: hh }]
                .concat(layout(rest, x, y + hh, w, h - hh, !horiz));
        }
        var rects = layout(data, 0, 0, W, H, W > H);

        var NS = "http://www.w3.org/2000/svg";
        var svg = document.createElementNS(NS, "svg");
        svg.setAttribute("viewBox", "0 0 " + W + " " + H);
        svg.setAttribute("class", "treemap");
        svg.setAttribute("width", "100%");
        svg.setAttribute("height", String(H));
        rects.forEach(function (r) {
            var rect = document.createElementNS(NS, "rect");
            rect.setAttribute("x", String(r.x + 2));
            rect.setAttribute("y", String(r.y + 2));
            rect.setAttribute("width", String(Math.max(0, r.w - 4)));
            rect.setAttribute("height", String(Math.max(0, r.h - 4)));
            rect.setAttribute("rx", "8");
            rect.setAttribute("fill", r.d.color || "#6366f1");
            svg.appendChild(rect);
            if (r.w > 64 && r.h > 40) {
                var t1 = document.createElementNS(NS, "text");
                t1.setAttribute("x", String(r.x + 12));
                t1.setAttribute("y", String(r.y + 24));
                t1.setAttribute("class", "tm-label");
                t1.setAttribute("fill", "#ffffff");
                t1.textContent = r.d.label || "";
                svg.appendChild(t1);
                var t2 = document.createElementNS(NS, "text");
                t2.setAttribute("x", String(r.x + 12));
                t2.setAttribute("y", String(r.y + 43));
                t2.setAttribute("class", "tm-value");
                t2.setAttribute("fill", "#ffffff");
                t2.textContent = "R$ " + shortMoney(r.d.value);
                svg.appendChild(t2);
            }
        });
        container.appendChild(svg);
    }

    // ── Redesenho responsivo ─────────────────────────────────
    function onResize(fn) {
        var pending;
        window.addEventListener("resize", function () {
            if (pending) cancelAnimationFrame(pending);
            pending = requestAnimationFrame(fn);
        });
    }

    window.RastroCharts = {
        donut: donut,
        multiLine: multiLine,
        treemap: treemap,
        onResize: onResize,
        readJson: function (id) {
            var el = document.getElementById(id);
            if (!el) return null;
            var text = el.textContent || el.innerText || "";
            try {
                return JSON.parse(text);
            } catch (e) {
                // Dentro de <script> o browser não decodifica entidades HTML;
                // th:text escapa as aspas (&quot;). Decodificamos as 5 entidades
                // padrão antes de parsear (seguro: nunca vai para innerHTML).
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
    };
})();
