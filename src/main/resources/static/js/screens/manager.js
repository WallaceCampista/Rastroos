// ─────────────────────────────────────────────────────────────
// Rastroo$ — /app/manager (Alfredo)
//   - Rola o thread para a última mensagem ao abrir.
//   - Confirm para forms com data-confirm (apagar conversa).
//   - Enter envia; Shift+Enter quebra linha.
//   - Envio por streaming: a resposta é escrita conforme chega,
//     sem recarregar a página. Sem JS, o form navega como antes.
// ─────────────────────────────────────────────────────────────
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var thread = document.getElementById("mgrThread");
        if (thread) {
            thread.scrollTop = thread.scrollHeight;
        }

        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                var message = form.getAttribute("data-confirm") || "Confirmar?";
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });

        // O seletor era ".mgr-composer .mgr-input" — e nunca casou com nada:
        // esse ancestral não existe no template, então Enter só quebrava linha.
        document.querySelectorAll("[data-mgr-form] .mgr-input").forEach(function (input) {
            input.addEventListener("keydown", function (event) {
                if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    if (input.value.trim().length > 0) {
                        input.form.requestSubmit();
                    }
                }
            });
        });

        wireStreaming(thread);
    });

    // ── Envio com resposta em streaming ──────────────────────

    function wireStreaming(thread) {
        var form = document.querySelector("[data-mgr-form]");
        if (!form || !thread || !window.RastroosStream) return;

        var chatId = thread.getAttribute("data-chat-id");
        if (!chatId) return;

        var input = form.querySelector(".mgr-input");
        var button = form.querySelector("button[type=submit]");
        var initial = thread.getAttribute("data-user-initial") || "·";
        var sending = false;

        form.addEventListener("submit", function (event) {
            var text = (input.value || "").trim();
            if (!text || sending) {
                if (sending) event.preventDefault();
                return;
            }
            event.preventDefault();
            send(text);
        });

        function setSending(on) {
            sending = on;
            input.disabled = on;
            if (button) button.disabled = on;
        }

        function bubbleOf(wrap) {
            return wrap.querySelector(".chat-bubble");
        }

        function send(text) {
            setSending(true);
            input.value = "";
            appendMessage(thread, "user", initial, text);

            var wrap = appendMessage(thread, "assistant", initial, "");
            var bubble = bubbleOf(wrap);
            bubble.appendChild(typingDots());

            var buffer = "";
            var painting = false;

            // Repinta no frame, não a cada pedaço: o modelo manda dezenas de
            // chunks por segundo e re-renderizar markdown em cada um trava.
            function paint() {
                painting = false;
                if (window.RastroosMarkdown) {
                    window.RastroosMarkdown.render(bubble, buffer);
                } else {
                    bubble.textContent = buffer;
                }
                thread.scrollTop = thread.scrollHeight;
            }

            window.RastroosStream.post(
                "/api/v1/chats/" + encodeURIComponent(chatId) + "/messages/stream",
                { message: text },
                {
                    csrf: window.RastroosStream.csrfToken(form),
                    onDelta: function (chunk) {
                        buffer += chunk;
                        if (!painting) {
                            painting = true;
                            window.requestAnimationFrame(paint);
                        }
                    },
                    onDone: function (full) {
                        buffer = full || buffer;
                        paint();
                        setSending(false);
                        input.focus();
                    },
                    onError: function () {
                        // Sem nada escrito ainda, a página cheia dá a resposta
                        // (e a mensagem de contingência) sem inventar texto aqui.
                        if (!buffer) {
                            window.location.reload();
                            return;
                        }
                        paint();
                        setSending(false);
                    },
                }
            );
        }
    }

    function appendMessage(thread, role, initial, text) {
        var wrap = document.createElement("div");
        wrap.className = "chat-msg " + (role === "user" ? "chat-msg-user" : "chat-msg-asst");

        var avatar = document.createElement("div");
        avatar.setAttribute("aria-hidden", "true");
        if (role === "user") {
            avatar.className = "chat-avatar user-avatar";
            avatar.textContent = initial;
        } else {
            avatar.className = "chat-avatar mgr-avatar";
            avatar.appendChild(star());
        }

        var bubble = document.createElement("div");
        bubble.className = "chat-bubble";
        if (role === "user" || !window.RastroosMarkdown) {
            bubble.textContent = text;
        } else {
            window.RastroosMarkdown.render(bubble, text);
        }

        wrap.append(avatar, bubble);
        thread.appendChild(wrap);
        thread.scrollTop = thread.scrollHeight;
        return wrap;
    }

    /** Mesma estrela do avatar do Alfredo no template (SVG montado por nó). */
    function star() {
        var NS = "http://www.w3.org/2000/svg";
        var svg = document.createElementNS(NS, "svg");
        svg.setAttribute("width", "16");
        svg.setAttribute("height", "16");
        svg.setAttribute("viewBox", "0 0 24 24");
        svg.setAttribute("fill", "none");
        svg.setAttribute("stroke", "currentColor");
        svg.setAttribute("stroke-width", "2");
        svg.setAttribute("stroke-linecap", "round");
        svg.setAttribute("stroke-linejoin", "round");
        var path = document.createElementNS(NS, "path");
        path.setAttribute("d", "M12 3l2.4 5.6 6 .5-4.6 3.9 1.4 5.9L12 16.9 6.8 18.9l1.4-5.9L3.6 9.1l6-.5z");
        svg.appendChild(path);
        return svg;
    }

    function typingDots() {
        var dots = document.createElement("span");
        dots.className = "alf-typing";
        dots.append(document.createElement("span"),
                    document.createElement("span"),
                    document.createElement("span"));
        return dots;
    }
})();
