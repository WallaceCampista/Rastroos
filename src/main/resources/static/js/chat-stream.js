/* ─────────────────────────────────────────────────────────────
   Rastroo$ — consumo do chat em streaming (SSE sobre fetch)
   ─────────────────────────────────────────────────────────────
   O servidor devolve text/event-stream num POST, então não dá
   para usar EventSource (que só faz GET): lemos o corpo com
   fetch + ReadableStream e cortamos os eventos na mão.

   Protocolo (ver ChatRestController):
     event: delta  data: {"text":"pedaço"}
     event: done   data: {"text":"resposta completa"}   ← autoritativa
     event: error  data: {"text":""}

   O `done` é quem manda: a tela re-renderiza com ele no fim. Assim
   uma queda no meio do caminho não deixa resposta pela metade —
   o texto que o servidor persistiu é o que fica na tela.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    /** Token CSRF do formulário mais próximo (ou de qualquer um da página). */
    function csrfToken(scope) {
        const input = (scope && scope.querySelector('input[name="_csrf"]'))
            || document.querySelector('input[name="_csrf"]');
        return input ? input.value : '';
    }

    /**
     * @param {string} url     endpoint de streaming
     * @param {object} body    corpo JSON
     * @param {object} handlers {onDelta, onDone, onError, csrf}
     * @returns {Promise<void>} resolve quando o fluxo termina
     */
    async function post(url, body, handlers) {
        const on = handlers || {};
        const token = on.csrf || csrfToken(null);

        let resp;
        try {
            resp = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'X-CSRF-TOKEN': token,
                    'X-Requested-With': 'fetch',
                },
                body: JSON.stringify(body),
            });
        } catch (err) {
            if (on.onError) on.onError('network');
            return;
        }

        if (!resp.ok || !resp.body) {
            if (on.onError) on.onError(String(resp.status));
            return;
        }

        const reader = resp.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let done = false;

        // Eventos são separados por linha em branco; um pedaço da rede pode
        // cortar no meio de um, então só processamos blocos completos.
        for (;;) {
            const chunk = await reader.read();
            if (chunk.done) break;
            buffer += decoder.decode(chunk.value, { stream: true });

            let sep;
            while ((sep = buffer.indexOf('\n\n')) >= 0) {
                const raw = buffer.slice(0, sep);
                buffer = buffer.slice(sep + 2);

                let event = 'message';
                let data = '';
                for (const line of raw.split('\n')) {
                    if (line.startsWith('event:')) event = line.slice(6).trim();
                    else if (line.startsWith('data:')) data += line.slice(5).trim();
                }
                if (!data) continue;

                let text = '';
                try {
                    text = (JSON.parse(data) || {}).text || '';
                } catch (e) {
                    continue;   // chunk malformado não derruba o fluxo
                }

                if (event === 'delta') {
                    if (on.onDelta) on.onDelta(text);
                } else if (event === 'done') {
                    done = true;
                    if (on.onDone) on.onDone(text);
                } else if (event === 'error') {
                    done = true;
                    if (on.onError) on.onError('server');
                }
            }
        }

        // Fluxo cortado antes do `done`: quem chama decide o que fazer com o
        // que já apareceu na tela.
        if (!done && on.onError) on.onError('truncated');
    }

    window.RastroosStream = { post: post, csrfToken: csrfToken };
})();
