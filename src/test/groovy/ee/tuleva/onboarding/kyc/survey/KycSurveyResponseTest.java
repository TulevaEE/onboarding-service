package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PepStatus.IS_NOT_PEP;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Address;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.AddressDetails;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.AddressValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Citizenship;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.CountriesValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Email;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.EmailValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.OptionValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PepSelfDeclaration;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PhoneNumber;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.TextValue;
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

  @Test
  void identityAccessorsReturnAnswerValues() {
    var response =
        new KycSurveyResponse(
            List.of(
                new Citizenship(new CountriesValue("COUNTRIES", List.of("EE", "FI"))),
                new Address(
                    new AddressValue(
                        "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
                new Email(new EmailValue("TEXT", "test@example.com")),
                new PhoneNumber(new TextValue("TEXT", "+37255555555")),
                new PepSelfDeclaration(new OptionValue<>("OPTION", IS_NOT_PEP))));

    assertThat(response.citizenship()).contains(List.of("EE", "FI"));
    assertThat(response.address())
        .contains(new AddressDetails("Street 1", "Tallinn", "12345", "EE"));
    assertThat(response.email()).contains("test@example.com");
    assertThat(response.phoneNumber()).contains("+37255555555");
    assertThat(response.pepSelfDeclaration()).contains(IS_NOT_PEP);
  }

  @Test
  void identityAccessorsReturnEmptyWhenAnswersAreMissing() {
    var response =
        new KycSurveyResponse(List.of(new Email(new EmailValue("TEXT", "test@example.com"))));

    assertThat(response.citizenship()).isEmpty();
    assertThat(response.address()).isEmpty();
    assertThat(response.phoneNumber()).isEmpty();
    assertThat(response.pepSelfDeclaration()).isEmpty();
  }

  @Test
  void identityAccessorsReturnEmptyForNullAnswerValues() {
    var response =
        new KycSurveyResponse(
            List.of(
                new Citizenship(null),
                new Address(new AddressValue("ADDRESS", null)),
                new Email(null),
                new PhoneNumber(null),
                new PepSelfDeclaration(new OptionValue<>("OPTION", null))));

    assertThat(response.citizenship()).isEmpty();
    assertThat(response.address()).isEmpty();
    assertThat(response.email()).isEmpty();
    assertThat(response.phoneNumber()).isEmpty();
    assertThat(response.pepSelfDeclaration()).isEmpty();
  }
}
