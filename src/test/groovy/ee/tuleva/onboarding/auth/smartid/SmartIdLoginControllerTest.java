package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aCallback;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aNotificationSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aRememberedAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(SmartIdLoginController.class)
@WithMockUser
class SmartIdLoginControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @MockitoBean private SmartIdLoginStarter smartIdLoginStarter;
  @MockitoBean private SmartIdDeviceLinks smartIdDeviceLinks;
  @MockitoBean private RememberedSmartIdAccounts rememberedSmartIdAccounts;
  @MockitoBean private GenericSessionStore sessionStore;

  private final SmartIdSession deviceLinkSession = aDeviceLinkSession(Instant.EPOCH);
  private final SmartIdSession notificationSession = aNotificationSession(Instant.EPOCH);

  @Test
  void startingADeviceLinkLoginReturnsTheSameDeviceLink() throws Exception {
    given(smartIdLoginStarter.startDeviceLinkLogin("et")).willReturn(deviceLinkSession);
    given(smartIdDeviceLinks.web2AppLink(deviceLinkSession))
        .willReturn(URI.create("https://smart-id.com/device-link/?deviceLinkType=Web2App"));

    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"DEVICE_LINK\",\"language\":\"et\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flow").value("DEVICE_LINK"))
        .andExpect(
            jsonPath("$.web2AppLink")
                .value("https://smart-id.com/device-link/?deviceLinkType=Web2App"))
        .andExpect(jsonPath("$.verificationCode").doesNotExist());

    verify(sessionStore).save(deviceLinkSession);
  }

  @Test
  void startingANotificationLoginPushesToTheRememberedAccount() throws Exception {
    given(rememberedSmartIdAccounts.current()).willReturn(Optional.of(aRememberedAccount()));
    given(smartIdLoginStarter.startNotificationLogin(aRememberedAccount()))
        .willReturn(notificationSession);

    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"NOTIFICATION\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flow").value("NOTIFICATION"))
        .andExpect(jsonPath("$.verificationCode").value("1234"))
        .andExpect(jsonPath("$.web2AppLink").doesNotExist());

    verify(sessionStore).save(notificationSession);
  }

  @Test
  void startingANotificationLoginWithoutARememberedAccountIsUnauthorized() throws Exception {
    given(rememberedSmartIdAccounts.current()).willReturn(Optional.empty());

    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"NOTIFICATION\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors[0].code").value("auth.session.not.found"));

    verify(smartIdLoginStarter, never()).startNotificationLogin(any());
  }

  @Test
  void startingALoginWithoutAFlowIsABadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"language\":\"et\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void qrCodeReturnsAFreshDeviceLinkForTheCurrentSession() throws Exception {
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(deviceLinkSession));
    given(smartIdDeviceLinks.qrCodeLink(deviceLinkSession))
        .willReturn(URI.create("https://smart-id.com/device-link/?deviceLinkType=QR"));

    mockMvc
        .perform(get("/v1/smart-id/login/qr-code"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.deviceLink").value("https://smart-id.com/device-link/?deviceLinkType=QR"));
  }

  @Test
  void qrCodeWithoutASessionIsUnauthorized() throws Exception {
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/v1/smart-id/login/qr-code"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors[0].code").value("auth.session.not.found"));
  }

  @Test
  void callbackIsAcceptedAndTheSessionSaved() throws Exception {
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(deviceLinkSession));

    mockMvc
        .perform(
            post("/v1/smart-id/login/callback")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aCallback())))
        .andExpect(status().isNoContent());

    assertThat(deviceLinkSession.getUserChallengeVerifier())
        .isEqualTo(aCallback().userChallengeVerifier());
    verify(sessionStore).save(deviceLinkSession);
  }

  @Test
  void rejectedCallbackIsUnauthorized() throws Exception {
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(deviceLinkSession));

    mockMvc
        .perform(
            post("/v1/smart-id/login/callback")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new SmartIdCallback("wrong-token", "digest", "verifier"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors[0].code").value("smart.id.callback.invalid"));

    assertThat(deviceLinkSession.getUserChallengeVerifier()).isNull();
    verify(sessionStore, never()).save(any());
  }

  @Test
  void callbackWithMissingFieldsIsABadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/smart-id/login/callback")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"value\":\"token\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rememberedAccountIsReturnedWhenPresent() throws Exception {
    given(rememberedSmartIdAccounts.current()).willReturn(Optional.of(aRememberedAccount()));

    mockMvc
        .perform(get("/v1/smart-id/login/remembered-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Aadu"))
        .andExpect(jsonPath("$.lastName").value("Kadakas"))
        .andExpect(jsonPath("$.personalCode").doesNotExist())
        .andExpect(jsonPath("$.documentNumber").doesNotExist());
  }

  @Test
  void rememberedAccountIsNoContentWhenAbsent() throws Exception {
    given(rememberedSmartIdAccounts.current()).willReturn(Optional.empty());

    mockMvc.perform(get("/v1/smart-id/login/remembered-account")).andExpect(status().isNoContent());
  }

  @Test
  void rememberedAccountCanBeForgotten() throws Exception {
    mockMvc
        .perform(delete("/v1/smart-id/login/remembered-account").with(csrf()))
        .andExpect(status().isNoContent());

    verify(rememberedSmartIdAccounts).forget();
  }
}
