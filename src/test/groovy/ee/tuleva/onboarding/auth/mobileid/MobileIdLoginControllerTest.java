package ee.tuleva.onboarding.auth.mobileid;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileIdLoginController.class)
@WithMockUser
class MobileIdLoginControllerTest {

  private static final String PERSONAL_CODE = "39001010000";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RememberedMobileIdPhones rememberedPhones;

  @Test
  void saysWhetherThisBrowserRemembersAPhoneForThePersonalCode() throws Exception {
    given(rememberedPhones.isRemembered(PERSONAL_CODE)).willReturn(true);

    mockMvc
        .perform(
            post("/v1/mobile-id/login/remembered")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"personalCode\":\"" + PERSONAL_CODE + "\"}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"remembered\":true}", true));
  }

  @Test
  void saysNoWhenThisBrowserRemembersNoPhoneForThePersonalCode() throws Exception {
    given(rememberedPhones.isRemembered(PERSONAL_CODE)).willReturn(false);

    mockMvc
        .perform(
            post("/v1/mobile-id/login/remembered")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"personalCode\":\"" + PERSONAL_CODE + "\"}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"remembered\":false}", true));
  }

  @Test
  void refusesAnythingButAValidEstonianPersonalCode() throws Exception {
    mockMvc
        .perform(
            post("/v1/mobile-id/login/remembered")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"personalCode\":\"38888888888\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("ValidPersonalCode"))
        .andExpect(jsonPath("$.errors[0].path").value("personalCode"));

    verifyNoInteractions(rememberedPhones);
  }
}
