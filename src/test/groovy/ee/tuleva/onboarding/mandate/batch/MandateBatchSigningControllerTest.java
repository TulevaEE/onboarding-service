package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.response.MandateSignatureStatus.SIGNATURE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.JwtTokenGenerator;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;

// database level integration tests are in MandateBatchIntegrationTest
// these are high level unit tests
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
public class MandateBatchSigningControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper mapper;

  @MockBean private MandateBatchService mandateBatchService;

  @MockBean private GenericSessionStore sessionStore;

  @MockBean private LocaleResolver localeResolver;

  static HttpHeaders getHeaders() {
    var jwtToken = JwtTokenGenerator.generateDefaultJwtToken();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);

    return headers;
  }

  @Nested
  @DisplayName("mobile id")
  class MobileIdTests {

    @Test
    @DisplayName("start mobile id signature returns the mobile ID challenge code")
    void startMobileIdSignatureReturnsChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var phoneNumber = "+372 555 5555";
      var mockSession = MobileIdSignatureSession.builder().verificationCode("1234").build();

      when(mandateBatchService.mobileIdSign(eq(mandateBatchId), any(), eq(phoneNumber)))
          .thenReturn(mockSession);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/mobile-id", mandateBatchId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.challengeCode", is("1234")));

      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    @DisplayName("get mobile id signature status returns the status and challenge code")
    void getMobileIdSignatureStatusReturnsStatusAndChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var mockSession = MobileIdSignatureSession.builder().verificationCode("1234").build();

      when(sessionStore.get(MobileIdSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeResolver.resolveLocale(any())).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeMobileIdSignature(
              any(), eq(mandateBatchId), any(), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      mvc.perform(
              get("/v1/mandate-batches/{id}/signature/mobile-id/status", mandateBatchId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.statusCode", is(SIGNATURE.toString())))
          .andExpect(jsonPath("$.challengeCode", is("1234")));
    }
  }

  @Nested
  @DisplayName("smart id")
  class SmartIdTests {

    @Test
    @DisplayName("start smart id signature returns null challenge code")
    void startSmartIdSignatureReturnsNullChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);
      mockSession.setVerificationCode(null);

      when(mandateBatchService.smartIdSign(eq(mandateBatchId), any())).thenReturn(mockSession);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/smart-id", mandateBatchId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.challengeCode").doesNotExist());

      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    @DisplayName("get smart id signature status returns the status and challenge code")
    void getSmartIdSignatureStatusReturnsStatusAndChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);
      mockSession.setVerificationCode("1234");

      when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeResolver.resolveLocale(any())).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeSmartIdSignature(
              any(), eq(mandateBatchId), eq(mockSession), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      mvc.perform(
              get("/v1/mandate-batches/{id}/signature/smart-id/status", mandateBatchId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.statusCode", is(SIGNATURE.toString())))
          .andExpect(jsonPath("$.challengeCode", is("1234")));
    }
  }

  @Nested
  @DisplayName("id card")
  class IdCardTests {

    @Test
    @DisplayName("start id card signature returns the hash to be signed by the client")
    void startIdCardSignatureReturnsHash() throws Exception {
      var mandateBatchId = 1L;
      var clientCertificate = "clientCertificate";
      var startCommand = MandateFixture.sampleStartIdCardSignCommand(clientCertificate);
      var mockSession = IdCardSignatureSession.builder().hashToSignInHex("asdfg").build();

      when(mandateBatchService.idCardSign(eq(mandateBatchId), any(), eq(clientCertificate)))
          .thenReturn(mockSession);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/id-card", mandateBatchId)
                  .content(mapper.writeValueAsString(startCommand))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.hash", is("asdfg")));

      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    @DisplayName("finish id card signature returns the status code")
    void finishIdCardSignatureReturnsStatusCode() throws Exception {
      var mandateBatchId = 1L;
      var signedHash = "signedHash";
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand(signedHash);
      var mockSession = IdCardSignatureSession.builder().build();

      when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeResolver.resolveLocale(any())).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeIdCardSignature(
              any(), eq(mandateBatchId), eq(mockSession), eq(signedHash), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/id-card/status", mandateBatchId)
                  .content(mapper.writeValueAsString(finishCommand))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.statusCode", is(SIGNATURE.toString())));
    }
  }
}
