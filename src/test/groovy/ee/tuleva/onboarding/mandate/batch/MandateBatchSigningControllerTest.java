package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
public class MandateBatchSigningControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private JsonMapper mapper;
  @MockitoBean private MandateBatchSignatureService mandateBatchSignatureService;

  @Nested
  @DisplayName("mobile id")
  class MobileIdTests {

    @Test
    @DisplayName("start mobile id signature returns the mobile ID challenge code")
    void startMobileIdSignatureReturnsChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var mockSession = MobileIdSignatureSession.builder().verificationCode("1234").build();
      var mockResponse = new MobileSignatureResponse(mockSession.getVerificationCode());

      when(mandateBatchSignatureService.startMobileIdSignature(eq(mandateBatchId), any()))
          .thenReturn(mockResponse);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/mobile-id", mandateBatchId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.challengeCode", is("1234")));
    }

    @Test
    @DisplayName("get mobile id signature status returns the status and challenge code")
    void getMobileIdSignatureStatusReturnsStatusAndChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var mockSession = MobileIdSignatureSession.builder().verificationCode("1234").build();
      var mockResponse =
          new MobileSignatureStatusResponse(SIGNATURE, mockSession.getVerificationCode());

      when(mandateBatchSignatureService.getMobileIdSignatureStatus(eq(mandateBatchId), any()))
          .thenReturn(mockResponse);

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

      var mockResponse = new MobileSignatureResponse(mockSession.getVerificationCode());

      when(mandateBatchSignatureService.startSmartIdSignature(eq(mandateBatchId), any()))
          .thenReturn(mockResponse);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/smart-id", mandateBatchId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.challengeCode").doesNotExist());
    }

    @Test
    @DisplayName("get smart id signature status returns the status and challenge code")
    void getSmartIdSignatureStatusReturnsStatusAndChallengeCode() throws Exception {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);
      mockSession.setVerificationCode("1234");
      var mockResponse =
          new MobileSignatureStatusResponse(SIGNATURE, mockSession.getVerificationCode());

      when(mandateBatchSignatureService.getSmartIdSignatureStatus(any(), any()))
          .thenReturn(mockResponse);

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
    void startIdCardSignatureReturnsTheHashToSignAndItsHashFunction() throws Exception {
      var mandateBatchId = 1L;
      var startCommand = MandateFixture.sampleStartIdCardSignCommand("certificate");
      var mockResponse = new IdCardSignatureResponse("asdfg", "SHA-256");

      when(mandateBatchSignatureService.startIdCardSign(
              eq(mandateBatchId), any(), eq(startCommand)))
          .thenReturn(mockResponse);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/id-card", mandateBatchId)
                  .content(mapper.writeValueAsString(startCommand))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.hash", is("asdfg")))
          .andExpect(jsonPath("$.hashFunction", is("SHA-256")));
    }

    @Test
    void persistIdCardSignatureReturnsTheProcessingStatus() throws Exception {
      var mandateBatchId = 1L;
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand("signature");
      var mockResponse = new IdCardSignatureStatusResponse(OUTSTANDING_TRANSACTION);

      when(mandateBatchSignatureService.persistIdCardSignature(
              eq(mandateBatchId), eq(finishCommand), any()))
          .thenReturn(mockResponse);

      mvc.perform(
              put("/v1/mandate-batches/{id}/signature/id-card/signature", mandateBatchId)
                  .content(mapper.writeValueAsString(finishCommand))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.statusCode", is(OUTSTANDING_TRANSACTION.toString())));
    }

    @Test
    void getIdCardSignatureStatusReturnsTheProcessingStatus() throws Exception {
      var mandateBatchId = 1L;
      var mockResponse = new IdCardSignatureStatusResponse(SIGNATURE);

      when(mandateBatchSignatureService.getIdCardSignatureStatus(eq(mandateBatchId), any()))
          .thenReturn(mockResponse);

      mvc.perform(
              get("/v1/mandate-batches/{id}/signature/id-card/status", mandateBatchId)
                  .headers(getHeaders()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.statusCode", is(SIGNATURE.toString())));
    }
  }
}
