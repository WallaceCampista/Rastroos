package com.rastroos.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.rastroos.domain.repository.UserRepository;

/**
 * Carrega usuário por email. Retorna {@link UsernameNotFoundException}
 * para usuário inexistente — Spring Security cuida de uniformizar a
 * resposta de erro (não distingue de senha incorreta).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByEmailIgnoreCase(username)
                .map(u -> new CustomUserDetails(u, resolveTargetName(u)))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /** Nome do usuário-alvo (só para contas ACESSOR) — usado no banner da UI. */
    private String resolveTargetName(com.rastroos.domain.entity.User user) {
        if (user.getAccessesUserId() == null) {
            return null;
        }
        return users.findById(user.getAccessesUserId())
                .map(com.rastroos.domain.entity.User::getName)
                .orElse(null);
    }
}
