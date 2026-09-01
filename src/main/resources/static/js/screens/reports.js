// ─────────────────────────────────────────────────────────────
// Rastro$ — tela /app/reports
// Liga os dados JSON inline aos gráficos (RastroCharts): dois donuts
// (categoria e conta) e a linha fixo vs variável dos 6 meses.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    function init() {
        var C = window.RastroCharts;
        if (!C) return;
        var data = C.readJson("reportsChartData");
        if (!data) return;

        var catCanvas = document.getElementById("categoryDonut");
        var accCanvas = document.getElementById("accountDonut");
        var lineCanvas = document.getElementById("fixedVarLine");
        var weightEl = document.getElementById("weightTreemap");

        // Mostra só as 6 maiores fatias; o resto vira uma fatia "Outros" cinza,
        // para o anel manter o total correto sem uma legenda gigante.
        function capSlices(arr) {
            var sorted = arr.slice().sort(function (a, b) { return b.value - a.value; });
            if (sorted.length <= 6) return sorted;
            var top = sorted.slice(0, 6);
            var rest = sorted.slice(6).reduce(function (s, x) { return s + x.value; }, 0);
            top.push({ label: "Outros", color: "#6b7280", value: rest });
            return top;
        }

        var first = true;
        function draw() {
            var animate = first;   // anima só na 1ª renderização, não a cada resize
            first = false;
            if (catCanvas && Array.isArray(data.byCategory)) {
                C.donut(catCanvas, capSlices(data.byCategory), { centerLabel: "gasto", animate: animate });
            }
            if (accCanvas && Array.isArray(data.byAccount)) {
                C.donut(accCanvas, capSlices(data.byAccount), { centerLabel: "gasto", animate: animate });
            }
            if (weightEl && Array.isArray(data.byCategory)) {
                C.treemap(weightEl, data.byCategory);
            }
            if (lineCanvas && data.fixedVar) {
                C.multiLine(lineCanvas, {
                    labels: data.fixedVar.labels,
                    series: (data.fixedVar.series || []).map(function (s) {
                        return { color: s.color, values: s.values, area: true };
                    })
                });
            }
        }

        draw();
        C.onResize(draw);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
