package com.rastroos.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.security.CustomUserDetails;

import jakarta.servlet.FilterChain;

class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void geraTraceIdEcoaNoHeaderELimpaOMdcAoFim() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] traceDuringChain = new String[1];

        FilterChain chain = (request, response) ->
                traceDuringChain[0] = MDC.get(MdcFilter.TRACE_ID);

        filter.doFilter(req, res, chain);

        assertThat(traceDuringChain[0]).isNotBlank();
        assertThat(res.getHeader(MdcFilter.REQUEST_ID_HEADER)).isEqualTo(traceDuringChain[0]);
        // limpo após a requisição (não vaza para a próxima thread reutilizada)
        assertThat(MDC.get(MdcFilter.TRACE_ID)).isNull();
        assertThat(MDC.get(MdcFilter.USER_ID)).isNull();
    }

    @Test
    void reaproveitaXRequestIdDeEntrada() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(MdcFilter.REQUEST_ID_HEADER, "trace-abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seen = new String[1];

        filter.doFilter(req, res, (rq, rs) -> seen[0] = MDC.get(MdcFilter.TRACE_ID));

        assertThat(seen[0]).isEqualTo("trace-abc-123");
    }

    @Test
    void colocaUserIdNoMdcQuandoAutenticado() throws Exception {
        UUID id = UUID.randomUUID();
        User u = new User();
        u.setId(id);
        u.setEmail("alice@example.com");
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        CustomUserDetails principal = new CustomUserDetails(u);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        String[] userDuringChain = new String[1];
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (rq, rs) -> userDuringChain[0] = MDC.get(MdcFilter.USER_ID));

        assertThat(userDuringChain[0]).isEqualTo(id.toString());
        assertThat(MDC.get(MdcFilter.USER_ID)).isNull(); // limpo ao fim
    }
}
