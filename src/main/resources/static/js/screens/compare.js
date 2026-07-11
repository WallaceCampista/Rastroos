// ─────────────────────────────────────────────────────────────
// Rastro$ — tela /app/compare
// Liga o JSON inline à linha multi-série (recebido, gasto, saldo,
// aporte) dos últimos 6 meses. As barras de taxa de poupança são
// renderizadas em CSS (larguras via atributos Thymeleaf), sem canvas.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    function init() {
        var C = window.RastroCharts;
        if (!C) return;
        var data = C.readJson("compareChartData");
        if (!data) return;

        var canvas = document.getElementById("compareLine");
        if (!canvas) return;

        function draw() {
            C.multiLine(canvas, {
                labels: data.labels,
                series: (data.series || []).map(function (s, i) {
                    // recebido/gasto com área; saldo/aporte só linha
                    return { color: s.color, values: s.values, area: i < 2 };
                })
            });
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
