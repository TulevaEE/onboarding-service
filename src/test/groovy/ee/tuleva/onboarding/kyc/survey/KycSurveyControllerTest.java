package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KycSurveyController.class)
@AutoConfigureMockMvc
class KycSurveyControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private KycSurveyService kycSurveyService;

  private final AuthenticatedPerson authPerson = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  @DisplayName("POST /v1/kyc/surveys saves survey and returns 201")
  void submit_savesSurveyAndReturnsCreated() throws Exception {
    when(kycSurveyService.save(eq(authPerson.getUserId()), any(KycSurveyResponse.class)))
        .thenReturn(KycSurvey.builder().userId(authPerson.getUserId()).build());

    String requestBody =
        """
        {
          "answers": [
            {
              "type": "CITIZENSHIP",
              "value": {
                "type": "COUNTRIES",
                "value": ["EE", "FI"]
              }
            },
            {
              "type": "EMAIL",
              "value": {
                "type": "TEXT",
                "value": "test@example.com"
              }
            },
            {
              "type": "PEP_SELF_DECLARATION",
              "value": {
                "type": "OPTION",
                "value": "IS_NOT_PEP"
              }
            }
          ]
        }
        """;

    mvc.perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isCreated());

    verify(kycSurveyService).save(eq(authPerson.getUserId()), any(KycSurveyResponse.class));
  }

  @Test
  @DisplayName("POST /v1/kyc/surveys with full payload deserializes correctly")
  void submit_withFullPayload_deserializesCorrectly() throws Exception {
    when(kycSurveyService.save(eq(authPerson.getUserId()), any(KycSurveyResponse.class)))
        .thenReturn(KycSurvey.builder().userId(authPerson.getUserId()).build());

    String requestBody =
        """
        {
          "answers": [
            {
              "type": "CITIZENSHIP",
              "value": {
                "type": "COUNTRIES",
                "value": ["US", "CA"]
              }
            },
            {
              "type": "ADDRESS",
              "value": {
                "type": "ADDRESS",
                "value": {
                  "street": "123 Main St",
                  "city": "Anytown",
                  "state": "CA",
                  "postalCode": "12345",
                  "countryCode": "US"
                }
              }
            },
            {
              "type": "EMAIL",
              "value": {
                "type": "TEXT",
                "value": "hendrik@molder.eu"
              }
            },
            {
              "type": "PHONE_NUMBER",
              "value": {
                "type": "TEXT",
                "value": "+1234567890"
              }
            },
            {
              "type": "PEP_SELF_DECLARATION",
              "value": {
                "type": "OPTION",
                "value": "IS_NOT_PEP"
              }
            },
            {
              "type": "INVESTMENT_GOALS",
              "value": {
                "type": "OPTION",
                "value": "LONG_TERM"
              }
            },
            {
              "type": "INVESTABLE_ASSETS",
              "value": {
                "type": "OPTION",
                "value": "80_OR_MORE"
              }
            },
            {
              "type": "SOURCE_OF_INCOME",
              "value": {
                "type": "MULTI_OPTION",
                "value": ["SALARY", "INVESTMENTS"]
              }
            },
            {
              "type": "TERMS",
              "value": {
                "type": "OPTION",
                "value": "ACCEPTED"
              }
            }
          ]
        }
        """;

    mvc.perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isCreated());

    verify(kycSurveyService).save(eq(authPerson.getUserId()), any(KycSurveyResponse.class));
  }

  @Test
  @DisplayName("POST /v1/kyc/surveys with invalid type returns 400")
  void submit_withInvalidType_returnsBadRequest() throws Exception {
    String requestBody =
        """
        {
          "answers": [
            {
              "type": "INVALID_TYPE",
              "value": {
                "type": "TEXT",
                "value": "test"
              }
            }
          ]
        }
        """;

    mvc.perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /v1/kyc/surveys with invalid enum value returns 400")
  void submit_withInvalidEnumValue_returnsBadRequest() throws Exception {
    String requestBody =
        """
        {
          "answers": [
            {
              "type": "PEP_SELF_DECLARATION",
              "value": {
                "type": "OPTION",
                "value": "INVALID_PEP_STATUS"
              }
            }
          ]
        }
        """;

    mvc.perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /v1/kyc/surveys with malformed JSON returns 400")
  void submit_withMalformedJson_returnsBadRequest() throws Exception {
    String requestBody = "{ invalid json }";

    mvc.perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /v1/kyc/surveys with invalid country code returns 400")
  void submit_withInvalidCountryCode_returnsBadRequest() throws Exception {
    String requestBody =
        """
        {
          "answers": [
            {
              "type": "CITIZENSHIP",
              "value": {
                "type": "COUNTRIES",
                "value": ["XX", "YY"]
              }
            }
          ]
        }
        """;

    mvc.perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }
}
