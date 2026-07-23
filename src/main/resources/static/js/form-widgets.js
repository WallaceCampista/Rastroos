/* ─────────────────────────────────────────────────────────────
   Rastro$ — widgets de formulário (abas de tipo + grade de categorias)
   ─────────────────────────────────────────────────────────────
   Funciona na página do formulário (DOMContentLoaded) e dentro do
   modal (o modals.js chama RastroosForms.init(container) após injetar).
   Sem style inline: a cor da categoria vira --c via CSSOM (ok p/ CSP).
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const HINTS = {
        variable: 'Gasto pontual como alimentação, combustível, compras.',
        fixed: 'Conta recorrente — se repete automaticamente nos próximos meses.',
    };

    function initTabs(tabs) {
        if (tabs.dataset.init) return;
        tabs.dataset.init = '1';
        const form = tabs.closest('form');
        if (!form) return;
        const fixed = form.querySelector('input[type="checkbox"][name="fixed"]');
        const hint = form.querySelector('[data-tx-hint]');
        const buttons = tabs.querySelectorAll('[data-tx-tab]');

        const sync = () => {
            const isFixed = !!(fixed && fixed.checked);
            buttons.forEach((b) => b.classList.toggle('on', (b.dataset.txTab === 'fixed') === isFixed));
            if (hint) hint.textContent = isFixed ? HINTS.fixed : HINTS.variable;
        };
        buttons.forEach((btn) => {
            btn.addEventListener('click', () => {
                if (fixed) fixed.checked = (btn.dataset.txTab === 'fixed');
                sync();
            });
        });
        sync();
    }

    function initCatGrid(grid) {
        if (grid.dataset.init) return;
        grid.dataset.init = '1';
        const form = grid.closest('form');
        const input = form ? form.querySelector('[data-cat-input]') : null;
        const buttons = grid.querySelectorAll('.cat-btn');

        buttons.forEach((btn) => {
            const color = btn.getAttribute('data-color');
            if (color) btn.style.setProperty('--c', color);
            if (input && btn.getAttribute('data-cat') === input.value) btn.classList.add('on');
            btn.addEventListener('click', () => {
                buttons.forEach((b) => b.classList.remove('on'));
                btn.classList.add('on');
                if (input) input.value = btn.getAttribute('data-cat');
            });
        });
    }

    // Conta (cartão / boleto / recorrente): mostra os campos de cada tipo.
    // Vive aqui (e não em account-form.js) para funcionar também no modal.
    function initAccountKind(select) {
        if (select.dataset.init) return;
        select.dataset.init = '1';
        const scope = select.closest('form') || document;
        const fields = scope.querySelectorAll('[data-only-kind]');
        const apply = () => {
            const cur = select.value;
            fields.forEach((f) => {
                const allowed = (f.getAttribute('data-only-kind') || '')
                    .split(',').map((k) => k.trim());
                // style.display (não o atributo hidden): .form-row/.check-row
                // definem display, que sobrescreveria [hidden].
                f.style.display = allowed.indexOf(cur) === -1 ? 'none' : '';
            });
        };
        select.addEventListener('change', apply);
        apply();
    }

    // Modal de investimento: Tipo em pílulas (Cofrinho / CDI / …) →
    // grava no input hidden "kind" e alterna os campos [data-only-kind].
    function initInvKind(group) {
        if (group.dataset.init) return;
        group.dataset.init = '1';
        const form = group.closest('form');
        const input = form ? form.querySelector('input[name="kind"]') : null;
        const buttons = Array.from(group.querySelectorAll('[data-kind]'));
        const fields = (form || document).querySelectorAll('[data-only-kind]');
        const applyFields = (cur) => {
            fields.forEach((f) => {
                const allowed = (f.getAttribute('data-only-kind') || '').split(',').map((k) => k.trim());
                f.style.display = allowed.indexOf(cur) === -1 ? 'none' : '';
            });
        };
        const select = (kind) => {
            buttons.forEach((b) => b.classList.toggle('on', b.getAttribute('data-kind') === kind));
            if (input) input.value = kind;
            applyFields(kind);
        };
        buttons.forEach((b) => b.addEventListener('click', () => select(b.getAttribute('data-kind'))));
        let initial = input && input.value ? input.value : null;
        if (!initial || !buttons.some((b) => b.getAttribute('data-kind') === initial)) {
            initial = buttons.length ? buttons[0].getAttribute('data-kind') : 'PIGGY';
        }
        select(initial);
    }

    // Grade de amostras (cor/ícone): clica → grava no input hidden.
    function initSwatches(group) {
        if (group.dataset.init) return;
        group.dataset.init = '1';
        const form = group.closest('form');
        const name = group.getAttribute('data-swatch-input');
        const input = form ? form.querySelector('[name="' + name + '"]') : null;
        const swatches = group.querySelectorAll('.swatch');
        swatches.forEach((sw) => {
            const val = sw.getAttribute('data-swatch');
            const fill = sw.getAttribute('data-fill');
            if (fill) sw.style.background = fill;                 // CSSOM (CSP-safe)
            if (input && input.value === val) sw.classList.add('on');
            sw.addEventListener('click', () => {
                swatches.forEach((s) => s.classList.remove('on'));
                sw.classList.add('on');
                if (input) input.value = val;
            });
        });
    }

    // Modal de investimento: abas "criar novo" / "adicionar em existente".
    function initInvMode(tabs) {
        if (tabs.dataset.init) return;
        tabs.dataset.init = '1';
        const form = tabs.closest('form');
        if (!form) return;
        const createAction = form.getAttribute('action');
        const depositAction = '/app/investments/deposit';
        const secCreate = form.querySelector('[data-inv-section="create"]');
        const secExisting = form.querySelector('[data-inv-section="existing"]');
        const submitBtn = form.querySelector('[data-inv-submit]');
        const buttons = tabs.querySelectorAll('[data-inv-mode]');

        const disable = (sec, off) => {
            if (sec) sec.querySelectorAll('input, select, textarea').forEach((el) => { el.disabled = off; });
        };
        const apply = (mode) => {
            buttons.forEach((b) => b.classList.toggle('on', b.dataset.invMode === mode));
            if (secCreate) secCreate.hidden = mode !== 'create';
            if (secExisting) secExisting.hidden = mode !== 'existing';
            disable(secCreate, mode !== 'create');
            disable(secExisting, mode !== 'existing');
            form.setAttribute('action', mode === 'existing' ? depositAction : createAction);
            if (submitBtn) submitBtn.textContent = mode === 'existing' ? 'Adicionar' : 'Criar';
        };
        buttons.forEach((b) => b.addEventListener('click', () => apply(b.dataset.invMode)));
        apply('create');
    }

    // Modal de investimento: rendimento mensal estimado a partir do % do CDI.
    function initCdiCalc(form) {
        if (form.dataset.cdiInit) return;
        const pctEl = form.querySelector('[data-cdi-pct]');
        if (!pctEl) return;
        form.dataset.cdiInit = '1';
        const valEl = form.querySelector('[data-cdi-value]');
        const estEl = form.querySelector('[data-cdi-estimate]');
        const pctLabel = form.querySelector('[data-cdi-pct-label]');
        const rateEl = form.querySelector('[data-cdi-rate-label]');
        const monthlyEl = form.querySelector('[data-cdi-monthly]');
        const CDI_ANNUAL = 0.1075;
        const money = (n) => 'R$ ' + n.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        const recalc = () => {
            const pct = parseFloat(pctEl.value) || 0;
            const val = valEl ? (parseFloat(String(valEl.value).replace(',', '.')) || 0) : 0;
            const monthly = Math.pow(1 + CDI_ANNUAL * pct / 100, 1 / 12) - 1;
            const ret = val * monthly;
            if (pctLabel) pctLabel.textContent = pct + '%';
            if (estEl) estEl.textContent = money(ret);
            if (rateEl) rateEl.value = pct + '% CDI';
            if (monthlyEl) monthlyEl.value = ret.toFixed(2);
        };
        pctEl.addEventListener('input', recalc);
        if (valEl) valEl.addEventListener('input', recalc);
        recalc();
    }

    const RastroosForms = {
        init(root) {
            const scope = root || document;
            scope.querySelectorAll('[data-tx-tabs]').forEach(initTabs);
            scope.querySelectorAll('[data-cat-grid]').forEach(initCatGrid);
            scope.querySelectorAll('[data-account-kind]').forEach(initAccountKind);
            scope.querySelectorAll('[data-inv-kind]').forEach(initInvKind);
            scope.querySelectorAll('[data-swatch-group]').forEach(initSwatches);
            scope.querySelectorAll('[data-inv-mode-tabs]').forEach(initInvMode);
            scope.querySelectorAll('[data-inv-form]').forEach(initCdiCalc);
        },
    };

    window.RastroosForms = RastroosForms;
    document.addEventListener('DOMContentLoaded', () => RastroosForms.init(document));
})();
