/* ─────────────────────────────────────────────────────────────────────────
   Rastro$ — landing page (extraído de inline <script>)
   ──────────────────────────────────────────────────────────────────────────
   Diferença vs. mockup: os forms do drawer fazem POST real para
   /auth/login, /auth/signup, /auth/forgot (com CSRF embutido no template).
   Não há intercept de submit aqui — a navegação real é tratada pelo Spring.
   ───────────────────────────────────────────────────────────────────────── */
(() => {
  /* ── Nav scroll state ─────────────────────────────────────────────── */
  const nav = document.getElementById('nav');
  if (nav) {
    const onScroll = () => nav.classList.toggle('is-scrolled', window.scrollY > 8);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* ── Hero mockup parallax tilt ────────────────────────────────────── */
  const heroMock = document.getElementById('heroMock');
  const hmStage = document.getElementById('hmStage');
  if (heroMock && hmStage && !window.matchMedia('(hover: none)').matches) {
    let raf = 0;
    heroMock.addEventListener('mousemove', (e) => {
      const r = heroMock.getBoundingClientRect();
      const x = ((e.clientX - r.left) / r.width - 0.5) * 2;
      const y = ((e.clientY - r.top) / r.height - 0.5) * 2;
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        hmStage.style.transform = `rotateY(${x * 6}deg) rotateX(${-y * 6}deg)`;
      });
    });
    heroMock.addEventListener('mouseleave', () => {
      cancelAnimationFrame(raf);
      hmStage.style.transform = 'rotateY(0) rotateX(0)';
    });
  }

  /* ── Reveal on scroll ─────────────────────────────────────────────── */
  const reveals = document.querySelectorAll('.reveal');
  const io = new IntersectionObserver((entries) => {
    entries.forEach(en => {
      if (en.isIntersecting) {
        en.target.classList.add('is-visible');
        io.unobserve(en.target);
      }
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -80px 0px' });
  reveals.forEach(el => io.observe(el));

  /* ── Number counter ───────────────────────────────────────────────── */
  const counters = document.querySelectorAll('[data-count]');
  const formatNum = (n, target) => {
    if (target >= 1000) return Math.round(n).toLocaleString('pt-BR');
    if (target % 1 !== 0) return n.toFixed(1).replace('.', ',');
    return Math.round(n).toString();
  };
  const counterIO = new IntersectionObserver((entries) => {
    entries.forEach(en => {
      if (!en.isIntersecting) return;
      const el = en.target;
      const target = parseFloat(el.dataset.count);
      const dur = 1400;
      const start = performance.now();
      const ease = (t) => 1 - Math.pow(1 - t, 3);
      const step = (now) => {
        const t = Math.min(1, (now - start) / dur);
        const v = ease(t) * target;
        el.textContent = formatNum(v, target);
        if (t < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
      counterIO.unobserve(el);
    });
  }, { threshold: 0.4 });
  counters.forEach(el => counterIO.observe(el));

  /* ── Login drawer ─────────────────────────────────────────────────── */
  const overlay = document.getElementById('loginOverlay');
  if (!overlay) return;

  const openers = document.querySelectorAll('[data-open-login]');
  const closers = document.querySelectorAll('[data-close-login]');
  const views   = overlay.querySelectorAll('.login-view');

  const showView = (name) => {
    const current = overlay.querySelector('.login-view.is-active');
    const next    = overlay.querySelector(`.login-view[data-view="${name}"]`);
    if (!next || next === current) return;
    if (current) {
      current.classList.remove('is-active');
      current.classList.add('is-leaving');
      setTimeout(() => current.classList.remove('is-leaving'), 240);
    }
    next.classList.add('is-active');
    setTimeout(() => {
      const firstInput = next.querySelector('input:not([type=checkbox])');
      firstInput?.focus();
    }, 280);
  };

  const initialView = overlay.dataset.initialView || 'login';
  const open = (view) => {
    overlay.classList.add('is-open');
    overlay.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
    views.forEach(v => v.classList.remove('is-active', 'is-leaving'));
    const target = view
      || overlay.querySelector('.login-view[data-view="' + initialView + '"]')?.dataset.view
      || 'login';
    overlay.querySelector(`.login-view[data-view="${target}"]`)?.classList.add('is-active');
    setTimeout(() => {
      overlay.querySelector('.login-view.is-active input:not([type=checkbox])')?.focus();
    }, 380);
  };
  const close = () => {
    overlay.classList.remove('is-open');
    overlay.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  };

  openers.forEach(b => b.addEventListener('click', () => open(b.dataset.openLogin || null)));
  closers.forEach(b => b.addEventListener('click', close));
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && overlay.classList.contains('is-open')) close();
  });

  overlay.querySelectorAll('[data-go-view]').forEach(btn => {
    btn.addEventListener('click', () => showView(btn.dataset.goView));
  });

  if (overlay.dataset.openOnLoad === 'true') {
    open(initialView);
  }

  /* Show / hide password — funciona em qualquer input via data-pwd-toggle */
  const eyeOpen  = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';
  const eyeShut  = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 19c-6 0-10-7-10-7a18.46 18.46 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c6 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><path d="M1 1l22 22"/></svg>';
  overlay.querySelectorAll('[data-pwd-toggle]').forEach(btn => {
    btn.addEventListener('click', () => {
      const target = document.getElementById(btn.dataset.pwdToggle);
      if (!target) return;
      const isPwd = target.type === 'password';
      target.type = isPwd ? 'text' : 'password';
      btn.innerHTML = isPwd ? eyeShut : eyeOpen;
      btn.setAttribute('aria-label', isPwd ? 'Ocultar senha' : 'Mostrar senha');
    });
  });

  /* Password strength meter (signup) */
  const signupPwd = document.getElementById('signupPwd');
  const strength  = document.getElementById('signupStrength');
  const strengthLbl = strength?.querySelector('.login-strength-lbl');
  const scorePwd = (v) => {
    if (!v) return 0;
    let s = 0;
    if (v.length >= 8) s++;
    if (/[a-z]/.test(v) && /[A-Z]/.test(v)) s++;
    if (/\d/.test(v)) s++;
    if (/[^a-zA-Z0-9]/.test(v) || v.length >= 12) s++;
    return Math.min(s, 4);
  };
  const strengthLabels = ['Força', 'Fraca', 'OK', 'Boa', 'Forte'];
  signupPwd?.addEventListener('input', () => {
    const score = scorePwd(signupPwd.value);
    if (strength) strength.dataset.level = String(score);
    if (strengthLbl) strengthLbl.textContent = strengthLabels[score];
    checkMatch();
  });

  /* Confirmação de senha */
  const signupPwd2 = document.getElementById('signupPwd2');
  const matchEl    = document.getElementById('signupMatch');
  const checkMatch = () => {
    if (!signupPwd || !signupPwd2 || !matchEl) return true;
    const a = signupPwd.value, b = signupPwd2.value;
    if (!b) { matchEl.className = 'login-match'; matchEl.textContent = ''; return false; }
    if (a === b) {
      matchEl.className = 'login-match show ok';
      matchEl.textContent = '✓ As senhas coincidem';
      return true;
    }
    matchEl.className = 'login-match show err';
    matchEl.textContent = 'As senhas não coincidem';
    return false;
  };
  signupPwd2?.addEventListener('input', checkMatch);

  /* Habilitar/desabilitar botões conforme preenchimento */
  const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((v || '').trim());

  const loginPwdEl    = document.getElementById('loginPwd');
  const loginEmailEl  = document.getElementById('loginEmail');
  const loginSubmit   = document.getElementById('loginSubmit');
  const forgotEmailEl = document.getElementById('forgotEmail');
  const forgotSubmit  = document.getElementById('forgotSubmit');
  const signupSubmit  = document.getElementById('signupSubmit');
  const signupNameEl  = document.getElementById('signupName');
  const signupEmailEl = document.getElementById('signupEmail');

  const syncButtons = () => {
    if (loginSubmit)
      loginSubmit.disabled = !(isEmail(loginEmailEl?.value) && (loginPwdEl?.value || '').length > 0);
    if (forgotSubmit)
      forgotSubmit.disabled = !isEmail(forgotEmailEl?.value);
    if (signupSubmit) {
      const pwd = signupPwd?.value || '';
      const ok = (signupNameEl?.value || '').trim().length > 0
              && isEmail(signupEmailEl?.value)
              && pwd.length >= 8
              && pwd === (signupPwd2?.value || '');
      signupSubmit.disabled = !ok;
    }
  };

  [loginEmailEl, loginPwdEl, forgotEmailEl, signupNameEl, signupEmailEl, signupPwd, signupPwd2]
    .forEach(el => el?.addEventListener('input', syncButtons));
  syncButtons();

  /* Bloqueia o cadastro se as senhas não coincidirem (validação client-side). */
  const signupForm = overlay.querySelector('form[data-form="signup"]');
  signupForm?.addEventListener('submit', (e) => {
    if (!checkMatch()) {
      e.preventDefault();
      signupPwd2?.focus();
    }
  });

  /* Feedback visual no submit (evita duplo clique). */
  overlay.querySelectorAll('form').forEach(form => {
    form.addEventListener('submit', () => {
      const btn = form.querySelector('.login-submit');
      if (btn && !btn.disabled) {
        btn.dataset.original = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = 'Enviando…';
      }
    });
  });
})();
