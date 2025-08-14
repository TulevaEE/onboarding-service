package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.signature.response.SignatureStatus.SIGNATURE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
public class MandateBatchSigningControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper mapper;
  @MockBean private MandateBatchSignatureService mandateBatchSignatureService;

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
    @DisplayName("start id card signature returns the hash to be signed by the client")
    void startIdCardSignatureReturnsHash() throws Exception {
      var mandateBatchId = 1L;
      var clientCertificate = "clientCertificate";
      var startCommand = MandateFixture.sampleStartIdCardSignCommand(clientCertificate);
      var mockSession = IdCardSignatureSession.builder().hashToSignInHex("asdfg").build();
      var mockResponse =
          IdCardSignatureResponse.builder().hash(mockSession.getHashToSignInHex()).build();

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
          .andExpect(jsonPath("$.hash", is("asdfg")));
    }

    @Test
    @DisplayName(
        "id card signature status endpoint returns finalized status when processing is finished")
    void finishIdCardSignatureReturnsStatusCode() throws Exception {
      var mandateBatchId = 1L;
      var signedHash = "signedHash";
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand(signedHash);

      var mockResponse = IdCardSignatureStatusResponse.builder().statusCode(SIGNATURE).build();

      when(mandateBatchSignatureService.persistIdCardSignedHashAndGetProcessingStatus(
              eq(mandateBatchId), eq(finishCommand), any()))
          .thenReturn(mockResponse);

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
