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

        function draw() {
            if (catCanvas && Array.isArray(data.byCategory)) {
                C.donut(catCanvas, data.byCategory, { centerLabel: "gasto" });
            }
            if (accCanvas && Array.isArray(data.byAccount)) {
                C.donut(accCanvas, data.byAccount, { centerLabel: "gasto" });
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
