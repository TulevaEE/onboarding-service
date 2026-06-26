package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;

import ee.tuleva.onboarding.kyc.KycSurveyPurpose;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Address;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.AddressDetails;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.AddressValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Citizenship;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.CountriesValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Email;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.EmailValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.OptionValue;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PepSelfDeclaration;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PepStatus;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PhoneNumber;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.TextValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

public record KycSurveyResponse(
    @NotNull @Size(min = 1) List<@Valid KycSurveyResponseItem> answers, KycSurveyPurpose purpose)
    implements Serializable {

  public KycSurveyResponse {
    purpose = purpose == null ? PERSONAL_ONBOARDING : purpose;
  }

  public KycSurveyResponse(List<KycSurveyResponseItem> answers) {
    this(answers, PERSONAL_ONBOARDING);
  }

  public Optional<List<String>> citizenship() {
    return firstAnswer(Citizenship.class).map(Citizenship::value).map(CountriesValue::value);
  }

  public Optional<AddressDetails> address() {
    return firstAnswer(Address.class).map(Address::value).map(AddressValue::value);
  }

  public Optional<String> email() {
    return firstAnswer(Email.class).map(Email::value).map(EmailValue::value);
  }

  public Optional<String> phoneNumber() {
    return firstAnswer(PhoneNumber.class).map(PhoneNumber::value).map(TextValue::value);
  }

  public Optional<PepStatus> pepSelfDeclaration() {
    return firstAnswer(PepSelfDeclaration.class)
        .map(PepSelfDeclaration::value)
        .map(OptionValue::value);
  }

  private <T extends KycSurveyResponseItem> Optional<T> firstAnswer(Class<T> type) {
    return answers.stream().filter(type::isInstance).map(type::cast).findFirst();
  }
}
