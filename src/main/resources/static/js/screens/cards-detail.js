/* ─────────────────────────────────────────────────────────────
   Rastro$ — /app/cards
   Clique num card → busca os lançamentos da conta (fetch) e abre
   o detalhe logo abaixo do grid (equivale ao AccountDetailPanel do
   protótipo). Clicar de novo no mesmo card fecha. Degrada para
   navegação normal se o fetch falhar.
   ───────────────────────────────────────────────────────────── */
(function () {
    "use strict";

    var panel = document.querySelector("[data-account-detail]");
    if (!panel) return;
    var openUrl = null;

    function paintBindings(root) {
        // tint do cabeçalho (gradiente com a cor da conta) — antes do data-fill,
        // pois o head não tem data-fill, só data-detail-tint.
        root.querySelectorAll("[data-detail-tint]").forEach(function (el) {
            var c = el.getAttribute("data-detail-tint");
            if (c) el.style.background =
                "linear-gradient(135deg, color-mix(in oklch, " + c + " 20%, transparent), transparent)";
        });
        root.querySelectorAll("[data-fill]").forEach(function (el) {
            el.style.background = el.getAttribute("data-fill");
        });
        root.querySelectorAll("[data-bar-width]").forEach(function (el) {
            el.style.width = el.getAttribute("data-bar-width") + "%";
        });
    }

    function markOpen(card) {
        document.querySelectorAll(".card-tile.is-open").forEach(function (c) {
            c.classList.remove("is-open");
        });
        if (card) card.classList.add("is-open");
    }

    function close() {
        panel.hidden = true;
        panel.innerHTML = "";
        openUrl = null;
        markOpen(null);
    }

    async function open(card) {
        var url = card.getAttribute("data-account-detail-url");
        if (!url) return;
        if (openUrl === url) { close(); return; }   // toggle no mesmo card
        try {
            var resp = await fetch(url, { headers: { "X-Requested-With": "fetch" } });
            var doc = new DOMParser().parseFromString(await resp.text(), "text/html");
            var content = doc.querySelector(".acct-detail");
            if (!content) { window.location.href = url; return; }

            panel.innerHTML = "";
            panel.appendChild(document.importNode(content, true));
            paintBindings(panel);
            panel.hidden = false;
            openUrl = url;
            markOpen(card);

            panel.querySelectorAll("[data-account-detail-close]").forEach(function (b) {
                b.addEventListener("click", close);
            });
            panel.querySelectorAll("form[data-confirm]").forEach(function (f) {
                f.addEventListener("submit", function (e) {
                    if (!window.confirm(f.getAttribute("data-confirm") || "Confirmar?")) e.preventDefault();
                });
            });
            panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
        } catch (e) {
            window.location.href = url;
        }
    }

    document.querySelectorAll(".card-tile[data-account-detail-url]").forEach(function (card) {
        card.addEventListener("click", function () { open(card); });
    });
})();
