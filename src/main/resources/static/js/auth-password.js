/* Show / hide password — funciona em qualquer input dentro de .auth-input-wrap
   via botão [data-pwd-toggle]. Sem inline (CSP script-src 'self'). */
(function () {
  document.addEventListener('click', function (event) {
    var btn = event.target.closest('[data-pwd-toggle]');
    if (!btn) {
      return;
    }
    var wrap = btn.closest('.auth-input-wrap');
    var input = wrap ? wrap.querySelector('input') : null;
    if (!input) {
      return;
    }
    var reveal = input.type === 'password';
    input.type = reveal ? 'text' : 'password';
    btn.classList.toggle('is-visible', reveal);
    btn.setAttribute('aria-label', reveal ? 'Ocultar senha' : 'Mostrar senha');
    btn.setAttribute('aria-pressed', reveal ? 'true' : 'false');
  });
})();
