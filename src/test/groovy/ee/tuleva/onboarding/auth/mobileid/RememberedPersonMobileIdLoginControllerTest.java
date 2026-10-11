package ee.tuleva.onboarding.auth.mobileid;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RememberedPersonMobileIdLoginController.class)
@WithMockUser
class RememberedPersonMobileIdLoginControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MobileIdLoginStarter loginStarter;
  @MockitoBean private GenericSessionStore sessionStore;

  @Test
  void startsTheRememberedPersonsLoginAndKeepsItInTheSession() throws Exception {
    var session = new MobileIDSession("mid-session", "1234", MobileIdFixture.hash, "+37255555555");
    given(loginStarter.startForRememberedPerson()).willReturn(session);

    mockMvc
        .perform(
            post("/v1/mobile-id/login/remembered-person")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode").value("1234"));

    verify(sessionStore).save(session);
  }

  @Test
  void refusesAStartAnotherSiteCouldSendAsAPlainFormWithoutAPreflight() throws Exception {
    mockMvc
        .perform(
            post("/v1/mobile-id/login/remembered-person")
                .with(csrf())
                .contentType(APPLICATION_FORM_URLENCODED))
        .andExpect(status().isUnsupportedMediaType());
    mockMvc
        .perform(
            post("/v1/mobile-id/login/remembered-person")
                .with(csrf())
                .contentType(TEXT_PLAIN)
                .content("{}"))
        .andExpect(status().isUnsupportedMediaType());

    verifyNoInteractions(loginStarter);
  }
}
