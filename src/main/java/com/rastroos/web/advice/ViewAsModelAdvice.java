package com.rastroos.web.advice;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.ViewAsOption;

/**
 * Injeta em todas as telas os atributos do modo "ver como" (admin):
 * <ul>
 *   <li>{@code viewAsIsAdmin} — a conta logada é ADMIN (mostra o select);</li>
 *   <li>{@code viewAsUsers} — usuários selecionáveis (exclui ACESSOR);</li>
 *   <li>{@code viewAsCurrentId}/{@code viewAsName} — seleção ativa e nome do alvo.</li>
 * </ul>
 * Só consulta o banco quando o logado é admin.
 */
@ControllerAdvice(basePackages = "com.rastroos.web.controller")
public class ViewAsModelAdvice {

    private final CurrentUser currentUser;
    private final ObjectProvider<UserRepository> usersProvider;

    public ViewAsModelAdvice(CurrentUser currentUser, ObjectProvider<UserRepository> usersProvider) {
        this.currentUser = currentUser;
        this.usersProvider = usersProvider;
    }

    @ModelAttribute
    public void viewAsAttributes(Model model) {
        boolean admin = currentUser.isAdmin();
        model.addAttribute("viewAsIsAdmin", admin);
        if (!admin) {
            return;
        }
        UserRepository users = usersProvider.getIfAvailable();
        if (users == null) {
            return; // sem repo no contexto (ex.: @WebMvcTest)
        }
        UUID current = currentUser.viewAsUserId().orElse(null);
        UUID self = currentUser.id().orElse(null);
        List<ViewAsOption> options = users.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .filter(u -> u.getRole() != UserRole.ACESSOR)
                .filter(u -> !u.getId().equals(self))     // o próprio admin já é "Meus dados"
                .map(u -> new ViewAsOption(u.getId(), u.getName(), u.getEmail()))
                .toList();
        model.addAttribute("viewAsUsers", options);
        model.addAttribute("viewAsCurrentId", current);
        if (current != null) {
            users.findById(current).ifPresent(u -> model.addAttribute("viewAsName", u.getName()));
        }
    }
}
