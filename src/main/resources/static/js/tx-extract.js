/* ─────────────────────────────────────────────────────────────
   Rastro$ — extração de gasto por documento (clip) / foto (câmera)
   ─────────────────────────────────────────────────────────────
   Os botões [data-tx-extract] no modal "Lançar gasto" disparam o
   file input correspondente; ao escolher/tirar a foto, o arquivo é
   enviado a /app/expenses/extract e o servidor devolve o próprio
   formulário PRÉ-PREENCHIDO para o usuário validar/editar e salvar.

   Funciona por delegação no document → cobre o form no modal e na
   página. Degrada: sem JS, os botões simplesmente não aparecem
   (o form normal continua funcionando).
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const MAX_BYTES = 8 * 1024 * 1024; // espelha extraction.max-file-size-bytes

    const modal = () => window.RastroosModal || {};
    const formSelector = () => modal().FORM_SELECTOR || '.app-content form, .auth-card form';
    const titleSelector = () => modal().TITLE_SELECTOR || '.screen-title, .auth-title';

    const csrfOf = (form) => {
        const el = form.querySelector('input[name="_csrf"]');
        return el ? el.value : null;
    };

    const setLoading = (form, on) => {
        const loading = form.querySelector('[data-extract-loading]');
        if (loading) loading.hidden = !on;
        form.querySelectorAll('[data-tx-extract]').forEach((b) => { b.disabled = on; });
    };

    const showError = (form, msg) => {
        const box = form.querySelector('[data-extract-error]');
        if (box) { box.textContent = msg; box.hidden = false; }
    };
    const clearError = (form) => {
        const box = form.querySelector('[data-extract-error]');
        if (box) { box.textContent = ''; box.hidden = true; }
    };

    const applyResult = (form, html) => {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        const newForm = doc.querySelector(formSelector());
        if (!newForm) {
            setLoading(form, false);
            showError(form, 'Não consegui ler o arquivo. Tente outro ou preencha manualmente.');
            return;
        }
        if (modal().ensureStyles) modal().ensureStyles(doc);
        const inModal = !!form.closest('[data-modal-container]');
        if (inModal && modal().renderForm) {
            const t = doc.querySelector(titleSelector());
            modal().renderForm(t ? t.textContent.trim() : '', newForm);
        } else {
            form.replaceWith(newForm);
            if (window.RastroosForms) window.RastroosForms.init(newForm);
        }
    };

    const upload = async (form, source, file) => {
        const url = form.getAttribute('data-extract-url');
        if (!url || !file) return;
        clearError(form);
        if (file.size > MAX_BYTES) {
            showError(form, 'Arquivo muito grande (máx. 8 MB).');
            return;
        }
        const csrf = csrfOf(form);
        const fd = new FormData();
        fd.append('file', file);
        fd.append('source', source);
        if (csrf) fd.append('_csrf', csrf);

        setLoading(form, true);
        try {
            const headers = { 'X-Requested-With': 'fetch' };
            if (csrf) headers['X-CSRF-TOKEN'] = csrf; // CSRF via header (upload é multipart)
            const resp = await fetch(url, { method: 'POST', body: fd, headers: headers });
            applyResult(form, await resp.text());
        } catch (err) {
            setLoading(form, false);
            showError(form, 'Falha ao enviar o arquivo. Verifique a conexão e tente novamente.');
        }
    };

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-tx-extract]');
        if (!btn) return;
        e.preventDefault();
        const form = btn.closest('form');
        if (!form) return;
        const input = form.querySelector('input[data-extract-file="' + btn.getAttribute('data-tx-extract') + '"]');
        if (input) input.click();
    });

    document.addEventListener('change', (e) => {
        const input = e.target.closest('input[data-extract-file]');
        if (!input) return;
        const form = input.closest('form');
        const file = input.files && input.files[0];
        if (form && file) upload(form, input.getAttribute('data-extract-file'), file);
        input.value = ''; // permite re-selecionar o mesmo arquivo
    });
})();
