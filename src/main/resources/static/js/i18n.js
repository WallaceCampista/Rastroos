/* ─────────────────────────────────────────────────────────────
   Rastro$ — helpers de i18n no cliente
   ─────────────────────────────────────────────────────────────
   A internacionalização real vive no servidor (MessageSource).
   Este módulo expõe utilitários para formatar números/datas
   conforme o locale do usuário, e ler o locale do <html lang>.
   ───────────────────────────────────────────────────────────── */
(() => {
    'use strict';

    const html = document.documentElement;
    const locale = html.getAttribute('lang') || 'pt-BR';

    const moneyFormatter = new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: locale === 'pt-BR' ? 'BRL' : 'USD',
    });

    const numberFormatter = new Intl.NumberFormat(locale);

    const dateFormatter = new Intl.DateTimeFormat(locale, {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    });

    window.RastroosI18n = {
        locale,
        money(cents) {
            const value = Number(cents || 0) / 100;
            return moneyFormatter.format(value);
        },
        number(value) {
            return numberFormatter.format(value);
        },
        date(value) {
            const d = (value instanceof Date) ? value : new Date(value);
            return dateFormatter.format(d);
        },
    };
})();
