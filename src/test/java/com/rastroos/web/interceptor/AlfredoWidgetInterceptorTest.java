package com.rastroos.web.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

class AlfredoWidgetInterceptorTest {

    private final AlfredoWidgetInterceptor interceptor = new AlfredoWidgetInterceptor();

    private ModelAndView run(String viewName, String activeNav) {
        ModelAndView mav = new ModelAndView(viewName);
        if (activeNav != null) {
            mav.addObject("activeNav", activeNav);
        }
        interceptor.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object(), mav);
        return mav;
    }

    @Test
    void telaComResumo_publicaOrbeChaveERotulo() {
        ModelAndView mav = run("app/dashboard", "dashboard");

        assertThat(mav.getModel().get("alfredoWidget")).isEqualTo(true);
        assertThat(mav.getModel().get("alfredoScreen")).isEqualTo("dashboard");
        assertThat(mav.getModel().get("alfredoScreenLabel")).isEqualTo("Visão geral");
    }

    @Test
    void telaDoAlfredo_naoRenderizaOWidget() {
        ModelAndView mav = run("app/manager", "manager");

        assertThat(mav.getModel().get("alfredoWidget")).isEqualTo(false);
        assertThat(mav.getModel()).doesNotContainKey("alfredoScreen");
    }

    @Test
    void telaSemResumo_mantemOOrbeComoAtalhoDeChat() {
        ModelAndView mav = run("app/support", "support");

        assertThat(mav.getModel().get("alfredoWidget")).isEqualTo(true);
        assertThat(mav.getModel()).doesNotContainKey("alfredoScreen");
    }

    @Test
    void telaSemActiveNav_naoQuebraEMantemOOrbe() {
        ModelAndView mav = run("app/profile", null);

        assertThat(mav.getModel().get("alfredoWidget")).isEqualTo(true);
        assertThat(mav.getModel()).doesNotContainKey("alfredoScreen");
    }

    @Test
    void redirect_naoRecebeAtributos() {
        ModelAndView mav = run("redirect:/app/dashboard", "dashboard");

        assertThat(mav.getModel()).doesNotContainKey("alfredoWidget");
    }

    @Test
    void semModelAndView_naoExplode() {
        interceptor.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object(), null);
    }
}
