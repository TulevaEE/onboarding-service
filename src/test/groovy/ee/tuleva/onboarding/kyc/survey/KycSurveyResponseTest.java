package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Email;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.EmailValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class KycSurveyResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void purposeDefaultsToPersonalOnboardingWhenMissingInJson() throws Exception {
    var json =
        """
        {
          "answers": [
            { "type": "EMAIL", "value": { "type": "TEXT", "value": "test@example.com" } }
          ]
        }
        """;

    var response = objectMapper.readValue(json, KycSurveyResponse.class);

    assertThat(response.purpose()).isEqualTo(PERSONAL_ONBOARDING);
  }

  @Test
  void purposeParsesIdentityOnlyFromJson() throws Exception {
    var json =
        """
        {
          "answers": [
            { "type": "EMAIL", "value": { "type": "TEXT", "value": "test@example.com" } }
          ],
          "purpose": "IDENTITY_ONLY"
        }
        """;

    var response = objectMapper.readValue(json, KycSurveyResponse.class);

    assertThat(response.purpose()).isEqualTo(IDENTITY_ONLY);
  }

  @Test
  void answersOnlyConstructorDefaultsPurposeToPersonalOnboarding() {
    var email = new Email(new EmailValue("TEXT", "test@example.com"));

    var response = new KycSurveyResponse(List.of(email));

    assertThat(response.purpose()).isEqualTo(PERSONAL_ONBOARDING);
  }

  @Test
  void nullPurposeDefaultsToPersonalOnboarding() {
    var email = new Email(new EmailValue("TEXT", "test@example.com"));

    var response = new KycSurveyResponse(List.of(email), null);

    assertThat(response.purpose()).isEqualTo(PERSONAL_ONBOARDING);
  }
}
