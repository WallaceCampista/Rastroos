package com.rastroos.domain.service;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserTheme;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.web.dto.OnboardingCardDto;
import com.rastroos.web.dto.OnboardingView;
import com.rastroos.web.form.OnboardingProfileForm;

/**
 * Wizard de boas-vindas do primeiro acesso.
 *
 * <p>Enquanto {@code users.onboarding_completed_at} for NULL, o modal abre em
 * qualquer tela do app. Ele é <b>dispensável</b>: "Pular" grava a data do
 * mesmo jeito que "Concluir" — quem não quer configurar nada não é perseguido
 * pelo wizard a cada login.
 *
 * <p>Cartões e receita recorrente são criados pelos serviços donos daquelas
 * regras ({@link AccountService}, {@link IncomeSourceService}); aqui ficam só
 * o perfil, a aparência e o estado do wizard.
 */
@Service
public class OnboardingService {

    /** Quantidade de paletas oferecidas (espelha o array de {@code palettes.js}). */
    public static final int PALETTE_COUNT = 18;

    private final UserRepository users;
    private final AccountRepository accounts;
    private final IncomeSourceService incomeSources;
    private final AuthService auth;
    private final Clock clock;

    public OnboardingService(UserRepository users,
                             AccountRepository accounts,
                             IncomeSourceService incomeSources,
                             AuthService auth,
                             Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.incomeSources = incomeSources;
        this.auth = auth;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OnboardingView state(UUID userId) {
        User u = require(userId);
        List<OnboardingCardDto> cards = new ArrayList<>();
        for (Account a : accounts.findAllByUserIdOrderByNameAsc(userId)) {
            if (a.getKind().isCard()) {
                cards.add(new OnboardingCardDto(a.getId(), a.getName(), a.getKind(),
                        a.getColorHex(), a.getLast4(), a.getCloseDay(), a.getDueDay()));
            }
        }
        return new OnboardingView(u.getName(), u.getEmail(), u.getTheme(), u.getPaletteIndex(),
                cards, incomeSources.list(userId, YearMonth.now(clock)));
    }

    /**
     * Salva nome e, se o usuário pediu, a senha nova.
     *
     * @return chaves de mensagem dos problemas encontrados; vazio = salvou.
     *         {@code password.currentWrong} aponta para o campo "senha atual",
     *         {@code password.mismatch} para a confirmação, o resto para a
     *         senha nova.
     */
    @Transactional
    public List<String> saveProfile(UUID userId, OnboardingProfileForm form) {
        if (form.wantsPasswordChange()) {
            if (!equalsSafe(form.getNewPassword(), form.getNewPasswordConfirm())) {
                return List.of("password.mismatch");
            }
            List<String> errs = auth.changePassword(userId,
                    form.getCurrentPassword(), form.getNewPassword());
            if (!errs.isEmpty()) {
                return errs;
            }
        }
        auth.updateName(userId, form.getName());
        return List.of();
    }

    @Transactional
    public void saveAppearance(UUID userId, UserTheme theme, short paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= PALETTE_COUNT) {
            throw new IllegalArgumentException("onboarding.paletteInvalid");
        }
        User u = require(userId);
        u.setTheme(theme == null ? UserTheme.dark : theme);
        u.setPaletteIndex(paletteIndex);
        users.save(u);
    }

    /** Marca o wizard como vencido — tanto no "Concluir" quanto no "Pular". */
    @Transactional
    public void complete(UUID userId) {
        User u = require(userId);
        if (u.getOnboardingCompletedAt() == null) {
            u.setOnboardingCompletedAt(clock.instant());
            users.save(u);
        }
    }

    private User require(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("users.notFound"));
    }

    private static boolean equalsSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
