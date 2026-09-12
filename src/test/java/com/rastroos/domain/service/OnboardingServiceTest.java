package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.entity.enums.UserTheme;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.web.form.OnboardingProfileForm;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock private UserRepository users;
    @Mock private AccountRepository accounts;
    @Mock private IncomeSourceService incomeSources;
    @Mock private AuthService auth;

    private static final Instant NOW = Instant.parse("2026-05-15T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private final UUID alice = UUID.randomUUID();

    private OnboardingService service() {
        return new OnboardingService(users, accounts, incomeSources, auth, clock);
    }

    @Test
    void stateTrazSoOsCartoesCreditoEDebito() {
        when(users.findById(alice)).thenReturn(Optional.of(user()));
        when(accounts.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(
                account("Nubank", AccountKind.CARD),
                account("Débito BB", AccountKind.DEBIT),
                account("Aluguel", AccountKind.RECURRENT),
                account("Luz", AccountKind.BILL)));
        when(incomeSources.list(eq(alice), any(YearMonth.class))).thenReturn(List.of());

        var view = service().state(alice);

        assertThat(view.cards()).extracting("name").containsExactly("Nubank", "Débito BB");
        assertThat(view.name()).isEqualTo("Alice");
    }

    @Test
    void saveProfileSemSenhaSoAtualizaONome() {
        OnboardingProfileForm form = new OnboardingProfileForm();
        form.setName("  Alice Dias  ");

        List<String> errs = service().saveProfile(alice, form);

        assertThat(errs).isEmpty();
        verify(auth).updateName(alice, "  Alice Dias  ");
        verify(auth, never()).changePassword(any(), anyString(), anyString());
    }

    @Test
    void saveProfileComConfirmacaoDiferenteNaoTrocaSenhaNemNome() {
        OnboardingProfileForm form = new OnboardingProfileForm();
        form.setName("Alice");
        form.setCurrentPassword("Atual!123");
        form.setNewPassword("Nova!1234");
        form.setNewPasswordConfirm("Outra!1234");

        List<String> errs = service().saveProfile(alice, form);

        assertThat(errs).containsExactly("password.mismatch");
        verify(auth, never()).changePassword(any(), anyString(), anyString());
        verify(auth, never()).updateName(any(), anyString());
    }

    @Test
    void saveProfileComSenhaAtualErradaPropagaOErroENaoRenomeia() {
        OnboardingProfileForm form = new OnboardingProfileForm();
        form.setName("Alice");
        form.setCurrentPassword("errada");
        form.setNewPassword("Nova!1234");
        form.setNewPasswordConfirm("Nova!1234");
        when(auth.changePassword(alice, "errada", "Nova!1234"))
                .thenReturn(List.of("password.currentWrong"));

        List<String> errs = service().saveProfile(alice, form);

        assertThat(errs).containsExactly("password.currentWrong");
        verify(auth, never()).updateName(any(), anyString());
    }

    @Test
    void saveProfileComSenhaValidaTrocaSenhaENome() {
        OnboardingProfileForm form = new OnboardingProfileForm();
        form.setName("Alice");
        form.setCurrentPassword("Atual!123");
        form.setNewPassword("Nova!1234");
        form.setNewPasswordConfirm("Nova!1234");
        when(auth.changePassword(alice, "Atual!123", "Nova!1234")).thenReturn(List.of());

        assertThat(service().saveProfile(alice, form)).isEmpty();
        verify(auth).updateName(alice, "Alice");
    }

    @Test
    void saveAppearanceGravaTemaEPaleta() {
        User u = user();
        when(users.findById(alice)).thenReturn(Optional.of(u));

        service().saveAppearance(alice, UserTheme.light, (short) 7);

        assertThat(u.getTheme()).isEqualTo(UserTheme.light);
        assertThat(u.getPaletteIndex()).isEqualTo((short) 7);
        verify(users).save(u);
    }

    @Test
    void saveAppearanceComPaletaForaDoIntervaloRecusa() {
        assertThatThrownBy(() -> service()
                .saveAppearance(alice, UserTheme.dark, (short) OnboardingService.PALETTE_COUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("onboarding.paletteInvalid");
        verify(users, never()).save(any(User.class));
    }

    @Test
    void completeCarimbaADataUmaVezSo() {
        User u = user();
        when(users.findById(alice)).thenReturn(Optional.of(u));

        service().complete(alice);
        assertThat(u.getOnboardingCompletedAt()).isEqualTo(NOW);

        // Segunda chamada não reescreve (nem toca no banco de novo).
        service().complete(alice);
        verify(users).save(u);
    }

    // ── helpers ──────────────────────────────────────────────

    private User user() {
        User u = new User();
        u.setId(alice);
        u.setName("Alice");
        u.setEmail("alice@example.com");
        u.setPasswordHash("hash");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setTheme(UserTheme.dark);
        return u;
    }

    private static Account account(String name, AccountKind kind) {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setKind(kind);
        return a;
    }
}
