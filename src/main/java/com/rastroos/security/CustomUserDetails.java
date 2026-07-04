package com.rastroos.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Adaptador da entity {@link User} para o contrato {@link UserDetails} do
 * Spring Security. Expõe id e flag {@code passwordMustChange} para que
 * controllers/handlers possam reagir após o login.
 */
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final boolean passwordMustChange;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.status = user.getStatus();
        this.passwordMustChange = user.isPasswordMustChange();
        this.authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public boolean isPasswordMustChange() { return passwordMustChange; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return status != UserStatus.DISABLED; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return status == UserStatus.ACTIVE; }
}
